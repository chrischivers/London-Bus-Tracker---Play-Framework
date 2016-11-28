package streaming

import commons.Commons._
import akka.actor.Actor
import database.PolyLineIndexDB.PolyLine
import datadefinitions.BusDefinitions
import datadefinitions.BusDefinitions.{BusStop, BusRoute}
import datasource.SourceLine
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

  var StopList: List[BusStop] = List()
  var receivedFirstLine = false
  var pauseAutoProcessingUntil: Long = -1
  var speedUpNumber = 0
  var lastIndexSentForProcessing = -1
  var nextStopArrivalDueAt: Long = -1
  var busRegistration: String = _
  var currentRoute: BusRoute = _
  var currentDestination: String = _

  override def receive: Actor.Receive = {
    case sourceLine: SourceLine =>
      if (receivedLineValid(sourceLine)) {
        val busStop = BusDefinitions.busRouteDefinitions(sourceLine.busRoute).filter(x => x.busStop.busStopID == sourceLine.busStopID).head.busStop
        process(sourceLine.busRoute, sourceLine.arrival_TimeStamp, busStop, sourceLine.destination)
        pauseAutoProcessingUntil = -1
      }
    case indexOfNextStopToCalculate: Int =>
      val timeToPause = pauseAutoProcessingUntil - System.currentTimeMillis()
      if (pauseAutoProcessingUntil == -1 || timeToPause <= 0) handleNextStopCalculation(indexOfNextStopToCalculate)
      else in(Duration(timeToPause, MILLISECONDS)) {
        self ! indexOfNextStopToCalculate
      }
  }

  def buildStopList(busRoute: BusRoute) = {
    StopList = BusDefinitions.busRouteDefinitions(busRoute).sortBy(_.sequenceNumber).map(x=> x.busStop)
    // println("Veh: " + vehicle_ID + "StopList: " + StopList)
  }

  /**
   * Check that reeived line is valid for processing
   * @param sourceLine The received line
   * @return True if valid, False if not
   */
  def receivedLineValid(sourceLine: SourceLine): Boolean = {
    val busStop = BusDefinitions.busRouteDefinitions(sourceLine.busRoute).filter(x => x.busStop.busStopID == sourceLine.busStopID).head.busStop
    try {
      // If the first line for this vehicle has been received already (i.e. in progress)
      if (receivedFirstLine) {
        val indexOfStopCode = StopList.indexOf(busStop)
        assert(sourceLine.busRegistration == busRegistration)
        if (sourceLine.busRoute == currentRoute) {
          currentDestination = sourceLine.destination
          // If the next line received is as expected
          if (indexOfStopCode == lastIndexSentForProcessing + 1 && indexOfStopCode != StopList.length - 1) true

          // If the next line received is behind the auto processing - needs to pause to allow catch up
          else if (indexOfStopCode <= lastIndexSentForProcessing && indexOfStopCode != StopList.length - 1) {
            val predictionRequest = new PredictionRequest(currentRoute,busStop.busStopID, StopList(lastIndexSentForProcessing + 1).busStopID, sourceLine.arrival_TimeStamp.getDayCode, sourceLine.arrival_TimeStamp.getTimeOffset)
            val predictedDurtoNextStop_MS = KNNPredictionImpl.makePrediction(predictionRequest).getOrElse(DEFAULT_DURATION_WHERE_PREDICTION_NOT_AVAILABLE, 1)._1 * 1000
            pauseAutoProcessingUntil = sourceLine.arrival_TimeStamp + predictedDurtoNextStop_MS.toLong
            false
          }


          // If the next line received is ahead of the auto processing - sends speed up request to allow the vehicle to smoothly catch up
          else if (indexOfStopCode > lastIndexSentForProcessing + 1 && indexOfStopCode != StopList.length - 1) {
            val stopsDifference = indexOfStopCode - (lastIndexSentForProcessing + 1)

            val predictionRequest = new PredictionRequest(currentRoute, busStop.busStopID, StopList(indexOfStopCode + 1).busStopID, sourceLine.arrival_TimeStamp.getDayCode, sourceLine.arrival_TimeStamp.getTimeOffset)
            val durationToNextStop = KNNPredictionImpl.makePrediction(predictionRequest).getOrElse(DEFAULT_DURATION_WHERE_PREDICTION_NOT_AVAILABLE, 1)._1 * 1000
            val durationPerStop = durationToNextStop / (stopsDifference + 1)

            for (i <- 0 to stopsDifference) {
              process(currentRoute, nextStopArrivalDueAt + (i * durationPerStop).toLong, StopList(lastIndexSentForProcessing + 1), sourceLine.destination)
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
        currentRoute = sourceLine.busRoute
        currentDestination = sourceLine.destination
        busRegistration = sourceLine.busRegistration
        buildStopList(currentRoute)
        val indexOfStopCode = StopList.indexOf(busStop)
        if (indexOfStopCode != StopList.length - 1) true
        else false
      }

    } catch {
      case e: IndexOutOfBoundsException => endOfRouteKill(); false;
      case e: NoSuchElementException => endOfRouteKill(); false;
    }
  }

  /**
   * Allows auto processing to handle the next stop iint he calculation automatically
   * @param nextStopIndex The index of the next stop to handle
   */
  def handleNextStopCalculation(nextStopIndex: Int) = {
    if (nextStopIndex == lastIndexSentForProcessing + 1 && nextStopIndex < StopList.length) {
      val busRoute = currentRoute
      val arrivalTime = nextStopArrivalDueAt
      val busStop = StopList(nextStopIndex)
      val towards = currentDestination

      process(busRoute, arrivalTime, busStop, towards)

    } else if (nextStopIndex == StopList.length - 1) endOfRouteKill()

  }

  /**
   * Vehicle at the end of route. Send a kill message to the supervisor, which will result in a Poison Pill
   */
  def endOfRouteKill() = {
    //val lastStopDefinition = TFLDefinitions.PointDefinitionsMap.get(StopList.last).getOrElse()
    LiveStreamingCoordinatorImpl.vehicleSupervisor ! new KillMessage(busRegistration, currentRoute, "0", "0")
  }

  /**
   * Processes the route by packaging the object an sending to the Supervisor for queuing (which it then send to clients)
   * @param busRoute The Bus Route Object
   * @param arrivalTime The arrival time
   * @param busStop The Bus Stop Object
   */
  def process(busRoute:BusRoute, arrivalTime: Long, busStop: BusStop, towards: String) = {

    try {
      val indexOfStopCode = StopList.indexOf(busStop)
      lastIndexSentForProcessing = indexOfStopCode

      val polyLineToNextStop = BusDefinitions.busRouteDefinitions(busRoute)(indexOfStopCode).polyLineToNextStop.getOrElse(new PolyLine())
      val movementDataArray = getMovementDataArray(polyLineToNextStop)

      val nextStop = StopList(indexOfStopCode + 1)
      val indexOfNextStop = indexOfStopCode + 1

      val predictionRequest = new PredictionRequest(busRoute, busStop.busStopID, nextStop.busStopID, arrivalTime.getDayCode, arrivalTime.getTimeOffset)
      val predictedDurtoNextStop_MS = KNNPredictionImpl.makePrediction(predictionRequest).getOrElse(DEFAULT_DURATION_WHERE_PREDICTION_NOT_AVAILABLE, 1)._1 * 1000

      //Holds back until previous has finished (prevents interuptions)
      val transmitTime = if (arrivalTime < nextStopArrivalDueAt) nextStopArrivalDueAt else arrivalTime
      //println("Veh: " + vehicle_ID + ". transmitTime - System.currentTimeMillis(): " + (transmitTime - System.currentTimeMillis()))

      in(Duration(transmitTime - System.currentTimeMillis() + 2000, MILLISECONDS)) {

        try {
          val addedTime = if (speedUpNumber > 0) (predictedDurtoNextStop_MS.toLong * SPEED_UP_MODE_TIME_MULTIPLIER).toLong else predictedDurtoNextStop_MS.toLong
          nextStopArrivalDueAt = arrivalTime + addedTime
          if (speedUpNumber > 0) speedUpNumber = speedUpNumber - 1


          // Encodes as a package object and enqueues
          val pso = new PackagedStreamObject(busRegistration, nextStopArrivalDueAt.toString, movementDataArray, busRoute.routeID, busRoute.direction, towards, nextStop.busStopID, nextStop.busStopName)
          LiveStreamingCoordinatorImpl.pushToClients(pso)

          val relativeDuration = nextStopArrivalDueAt - System.currentTimeMillis()
          try {
            in(Duration(relativeDuration, MILLISECONDS)) {
              self ! indexOfNextStop
            }
          } catch {
            case e: NullPointerException => //Actor already killed. Do nothing
          }
        }
        catch {
          case e: IndexOutOfBoundsException => endOfRouteKill()
          case e: NoSuchElementException => endOfRouteKill()
        }
      }
    }
    catch {
      case e: IndexOutOfBoundsException => endOfRouteKill()
      case e: NoSuchElementException => endOfRouteKill()
    }


  }

  def in[U](duration: FiniteDuration)(body: => U): Unit =
    Akka.system.scheduler.scheduleOnce(duration)(body)

}