package streaming

import commons.Commons._
import akka.actor.Actor
import datadefinitions.tfl.TFLDefinitions
import datasource.tfl.TFLSourceLineImpl
import play.api.libs.concurrent.Akka
import prediction.{KNNPredictionImpl, PredictionRequest}
import play.api.Play.current
import scala.concurrent.duration._

/**
 * The Vehicle Actor - One exists for each vehicle currently in motion
 */
class VehicleActor extends Actor {

  import context.dispatcher

  val DEFAULT_DURATION_WHERE_PREDICTION_NOT_AVAILABLE: Double = 45
  val SPEED_UP_MODE_TIME_MULTIPLIER = 0.5

  var StopList: List[String] = List()
  var receivedFirstLine = false
  var pauseAutoProcessingUntil: Long = -1
  var speedUpNumber = 0
  var lastIndexSentForProcessing = -1
  var nextStopArrivalDueAt: Long = -1
  var vehicleID:String = _
  var currentRouteID: String = _
  var currentDirectionID: Int = _

  override def receive: Actor.Receive = {
    case sourceLine: TFLSourceLineImpl =>
      if (receivedLineValid(sourceLine)) {
        process(sourceLine.route_ID, sourceLine.direction_ID, sourceLine.arrival_TimeStamp, sourceLine.stop_Code)
        pauseAutoProcessingUntil = -1
      }
    case indexOfNextStopToCalculate: Int =>
      val timeToPause = pauseAutoProcessingUntil - System.currentTimeMillis()
      if (pauseAutoProcessingUntil == -1 || timeToPause <= 0) handleNextStopCalculation(indexOfNextStopToCalculate)
      else  in(Duration(timeToPause, MILLISECONDS)) {
        self ! indexOfNextStopToCalculate
      }
  }

  def buildStopList(routeID: String, directionID: Int) = {
    StopList = TFLDefinitions.RouteDefinitionMap(routeID, directionID).sortBy(_._1).map { case (sequence, stop, firstLast, polyline) => stop }
    // println("Veh: " + vehicle_ID + "StopList: " + StopList)
  }

  /**
   * Check that reeived line is valid for processing
   * @param sourceLine The received line
   * @return True if valid, False if not
   */
  def receivedLineValid(sourceLine: TFLSourceLineImpl): Boolean = {

    // If the first line for this vehicle has been received already (i.e. in progress)
    if (receivedFirstLine) {
      val indexOfStopCode = StopList.indexOf(sourceLine.stop_Code)
      assert(sourceLine.vehicle_Reg == vehicleID)
      if (sourceLine.route_ID == currentRouteID && sourceLine.direction_ID == currentDirectionID) {

        // If the next line received is as expected
        if (indexOfStopCode == lastIndexSentForProcessing + 1 && indexOfStopCode != StopList.length - 1) true

        // If the next line received is behind the auto processing - needs to pause to allow catch up
        else if (indexOfStopCode <= lastIndexSentForProcessing && indexOfStopCode != StopList.length - 1) {
          val predictionRequest = new PredictionRequest(currentRouteID, currentDirectionID, sourceLine.stop_Code, StopList(lastIndexSentForProcessing + 1), sourceLine.arrival_TimeStamp.getDayCode, sourceLine.arrival_TimeStamp.getTimeOffset)
          val predictedDurtoNextStop_MS = KNNPredictionImpl.makePrediction(predictionRequest).getOrElse(DEFAULT_DURATION_WHERE_PREDICTION_NOT_AVAILABLE, 1)._1 * 1000
          pauseAutoProcessingUntil = sourceLine.arrival_TimeStamp + predictedDurtoNextStop_MS.toLong
          false
        }


        // If the next line received is ahead of the auto processing - sends speed up request to allow the vehicle to smoothly catch up
        else if (indexOfStopCode > lastIndexSentForProcessing + 1 && indexOfStopCode != StopList.length - 1) {
          val stopsDifference = indexOfStopCode - (lastIndexSentForProcessing + 1)

          val predictionRequest = new PredictionRequest(currentRouteID, currentDirectionID, sourceLine.stop_Code, StopList(indexOfStopCode + 1), sourceLine.arrival_TimeStamp.getDayCode, sourceLine.arrival_TimeStamp.getTimeOffset)
          val durationToNextStop = KNNPredictionImpl.makePrediction(predictionRequest).getOrElse(DEFAULT_DURATION_WHERE_PREDICTION_NOT_AVAILABLE, 1)._1 * 1000
          val durationPerStop = durationToNextStop / (stopsDifference + 1)

          for (i <- 0 to stopsDifference) {
            process(currentRouteID, currentDirectionID, nextStopArrivalDueAt + (i * durationPerStop).toLong, StopList(lastIndexSentForProcessing + 1))
          }
          true
        }

        // If it is the last stop on the route, kill it
        else if (indexOfStopCode == StopList.length - 1) {
          endOfRouteKill() //Handle last stop
          false
        } else false

      } else {
        receivedFirstLine = false
        receivedLineValid(sourceLine)
      }

      // If first line has not been received, set up the vehicle definition
    } else {
      receivedFirstLine = true
      currentRouteID = sourceLine.route_ID
      currentDirectionID = sourceLine.direction_ID
      vehicleID = sourceLine.vehicle_Reg
      buildStopList(currentRouteID, currentDirectionID)
      val indexOfStopCode = StopList.indexOf(sourceLine.stop_Code)
      if (indexOfStopCode != StopList.length - 1) true
      else false
    }
  }

  /**
   * Allows auto processing to handle the next stop iint he calculation automatically
   * @param nextStopIndex The index of the next stop to handle
   */
  def handleNextStopCalculation(nextStopIndex: Int) = {
    if (nextStopIndex == lastIndexSentForProcessing + 1 && nextStopIndex != StopList.length - 1) {
      val routeID = currentRouteID
      val directionID = currentDirectionID
      val arrivalTime = nextStopArrivalDueAt
      val stopCode = StopList(nextStopIndex)
      process(routeID, directionID, arrivalTime, stopCode)
    } else if (nextStopIndex == StopList.length - 1) endOfRouteKill()

  }

  /**
   * Vehicle at the end of route. Send a kill message to the supervisor, which will result in a Poison Pill
   */
  def endOfRouteKill() = {
    val lastStopDefinition = TFLDefinitions.PointDefinitionsMap(StopList.last)
    LiveStreamingCoordinatorImpl.vehicleSupervisor ! new KillMessage(vehicleID, currentRouteID,lastStopDefinition.latitude, lastStopDefinition.longitude)
  }

  /**
   * Processes the route by packaging the object an sending to the Supervisor for queuing (which it then send to clients)
   * @param routeID The route ID
   * @param directionID The direction ID
   * @param arrivalTime The arrival time
   * @param stopCode The stop code
   */
  def process(routeID: String, directionID: Int, arrivalTime: Long, stopCode: String) = {

    val indexOfStopCode = StopList.indexOf(stopCode)
    lastIndexSentForProcessing = indexOfStopCode

    val polyLineToNextStop = TFLDefinitions.RouteDefinitionMap(routeID, directionID)(indexOfStopCode)._4
    val movementDataArray = getMovementDataArray(polyLineToNextStop)

    val nextStopCode = StopList(indexOfStopCode + 1)
    val indexOfNextStopCode = indexOfStopCode + 1

    val predictionRequest = new PredictionRequest(routeID, directionID, stopCode, nextStopCode, arrivalTime.getDayCode, arrivalTime.getTimeOffset)
    val predictedDurtoNextStop_MS = KNNPredictionImpl.makePrediction(predictionRequest).getOrElse(DEFAULT_DURATION_WHERE_PREDICTION_NOT_AVAILABLE, 1)._1 * 1000

    //Holds back until previous has finished (prevents interuptions)
    val transmitTime = if (arrivalTime < nextStopArrivalDueAt) nextStopArrivalDueAt else arrivalTime
    //println("Veh: " + vehicle_ID + ". transmitTime - System.currentTimeMillis(): " + (transmitTime - System.currentTimeMillis()))

    in(Duration(transmitTime - System.currentTimeMillis() + 2000, MILLISECONDS)) {

      val addedTime = if (speedUpNumber > 0) (predictedDurtoNextStop_MS.toLong * SPEED_UP_MODE_TIME_MULTIPLIER).toLong else predictedDurtoNextStop_MS.toLong
      nextStopArrivalDueAt = arrivalTime + addedTime
      if (speedUpNumber > 0) speedUpNumber = speedUpNumber - 1


      // Encodes as a package object and enqueues
      val pso = new PackagedStreamObject(vehicleID, nextStopArrivalDueAt.toString, movementDataArray, routeID, directionID, TFLDefinitions.PointDefinitionsMap(StopList.last).stopPointName, nextStopCode, TFLDefinitions.PointDefinitionsMap(nextStopCode).stopPointName)
      LiveStreamingCoordinatorImpl.pushToClients(pso)

      val relativeDuration = nextStopArrivalDueAt - System.currentTimeMillis()
      try {
        in(Duration(relativeDuration, MILLISECONDS)) {
          self ! indexOfNextStopCode
        }
      } catch {
        case e: NullPointerException => //Actor already killed. Do nothing
      }
    }


  }

  def in[U](duration: FiniteDuration)(body: => U): Unit =
    Akka.system.scheduler.scheduleOnce(duration)(body)

}