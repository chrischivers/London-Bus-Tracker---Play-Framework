package processes.tfl

import java.util.Date

import database.POINT_TO_POINT_DOCUMENT
import database.tfl.TFLInsertPointToPointDurationSupervisor
import datadefinitions.tfl.TFLDefinitions
import datasource.tfl.TFLSourceLineImpl
import org.apache.commons.lang3.time.DateUtils
import commons.Commons._
import processes.weather.Weather


object TFLProcessSourceLines {


  val MAXIMUM_AGE_OF_RECORDS_IN_HOLDING_BUFFER = 600000 //In Ms
  var numberNonMatches = 0


  /**
   * The holding buffer holds the temporary list of routes awaiting the next stop.
   * It is a Map of (Route ID, Vehicle Reg, Direciton ID) -> (Stop ID, ArrivalTimeStamp)
   */
  private var holdingBuffer: Map[(String, String, Int), (String, Long)] = Map()
  private var liveStreamCollectionEnabled: Boolean = false
  private var historicalDataStoringEnabled = false

  private val stopIgnoreList = TFLDefinitions.StopIgnoreList
  private val routeIgnoreList = TFLDefinitions.RouteIgnoreList
  private val publicHolidayList = TFLDefinitions.PublicHolidayList

  def getBufferSize: Int = holdingBuffer.size

  def apply(newLine: TFLSourceLineImpl) {

    if (validateLine(newLine)) {
      // Send to Live Streaming Coordinator if Enabled
      //TODO implement this
     // if (liveStreamCollectionEnabled) LiveStreamingCoordinatorImpl.scala.processSourceLine(newLine)

      // Process for historical data if Enabled
      if (historicalDataStoringEnabled && !isPublicHoliday(newLine)) processLineForHistoricalData(newLine)
    }
  }

  /**
   * Processes the validated line for historical data by comparing with holdingBuffer and, if necessary, inserting into the DB
   * @param newLine The validated Source Line
   */
  def processLineForHistoricalData(newLine: TFLSourceLineImpl) = {
    if (!holdingBuffer.contains(newLine.route_ID, newLine.vehicle_Reg, newLine.direction_ID)) {
      updateHoldingBufferAndPrune(newLine)
    } else {
      val existingValues = holdingBuffer(newLine.route_ID, newLine.vehicle_Reg, newLine.direction_ID)
      val existingStopCode = existingValues._1
      val existingArrivalTimeStamp = existingValues._2
      val existingPointSequence = TFLDefinitions.RouteDefinitionMap(newLine.route_ID, newLine.direction_ID).filter(x => x._2 == existingStopCode).head._1
      val newPointSequence = TFLDefinitions.RouteDefinitionMap(newLine.route_ID, newLine.direction_ID).filter(x => x._2 == newLine.stop_Code).last._1
      if (newPointSequence == existingPointSequence + 1) {
        val durationInSeconds = ((newLine.arrival_TimeStamp - existingArrivalTimeStamp) / 1000).toInt
        if (durationInSeconds > 0) {
          TFLInsertPointToPointDurationSupervisor.insertDoc(createPointToPointDocument(newLine.route_ID, newLine.direction_ID, existingStopCode, newLine.stop_Code, existingArrivalTimeStamp.getDayCode, existingArrivalTimeStamp.getTimeOffset, durationInSeconds))
          updateHoldingBufferAndPrune(newLine)
        } else {
          updateHoldingBufferAndPrune(newLine) // Replace existing values with new values
        }
      } else if (newPointSequence >= existingPointSequence) {
        updateHoldingBufferAndPrune(newLine) // Replace existing values with new values
      } else {
        // DO Nothing
      }

    }
  }
  

  /**
   * Updates the holding buffer. If it is not the final stop, the line is added to the holding buffer replacing the existing entry
   * THe holding buffer is pruned to eliminate old records
   * If it is the final stop, the entry is deleted from the holding buffer
   * @param line The source line
   */
  private def updateHoldingBufferAndPrune(line: TFLSourceLineImpl) = {
    if (!isFinalStop(line)) {
      holdingBuffer += ((line.route_ID, line.vehicle_Reg, line.direction_ID) ->(line.stop_Code, line.arrival_TimeStamp))
      val CUT_OFF: Long = System.currentTimeMillis() - MAXIMUM_AGE_OF_RECORDS_IN_HOLDING_BUFFER
      holdingBuffer = holdingBuffer.filter { case ((_), (_, time)) => time > CUT_OFF }
    } else {
      holdingBuffer -= ((line.route_ID, line.vehicle_Reg, line.direction_ID))
    }
  }

  /**
   * Checks the line is acceptable for inserting
   * @param line The source line
   * @return True if valid, false if not
   */
  private def validateLine(line: TFLSourceLineImpl): Boolean = {

    def inDefinitionFile(line: TFLSourceLineImpl): Boolean = {
      if (TFLDefinitions.RouteDefinitionMap.get(line.route_ID, line.direction_ID).isEmpty) {
        numberNonMatches += 1
        //logger.info("Cannot get definition. Line: " + line) //TODO Fix this
        false
      } else if (!TFLDefinitions.RouteDefinitionMap(line.route_ID, line.direction_ID).exists(x => x._2 == line.stop_Code)) {
        numberNonMatches += 1
        // println(line.route_ID + "," + line.direction_ID + ":    " + TFLDefinitions.RouteDefinitionMap(line.route_ID, line.direction_ID))
        // println("Non Match: " + line.route_ID + ", " + line.direction_ID + ", " + line.stop_Code)
        false
      }
      else true
    }


    def isWithinTimeThreshold(line: TFLSourceLineImpl): Boolean = {
      ((line.arrival_TimeStamp - System.currentTimeMillis) / 1000) <= TFLProcessVariables.LINE_TOLERANCE_IN_RELATION_TO_CURRENT_TIME
    }

    def isNotOnIgnoreLists(line: TFLSourceLineImpl): Boolean = {
      if (routeIgnoreList.contains(line.route_ID) || stopIgnoreList.contains(line.stop_Code)) false else true
    }

    if (!isNotOnIgnoreLists(line)) return false
    if (!inDefinitionFile(line)) return false
    if (!isWithinTimeThreshold(line)) return false
    true
  }

  private def isFinalStop(line: TFLSourceLineImpl): Boolean = TFLDefinitions.RouteDefinitionMap(line.route_ID, line.direction_ID).filter(x => x._2 == line.stop_Code).head._3.contains("LAST")

  private def isPublicHoliday(line: TFLSourceLineImpl): Boolean = {

    val date = new Date(line.arrival_TimeStamp)
    for (pubHolDate <- publicHolidayList) if (DateUtils.isSameDay(date, pubHolDate)) return true
    false
  }


  private def createPointToPointDocument(route_ID: String, direction_ID: Int, from_Point_ID: String, to_Point_ID: String, day_Type: String, observed_Time: Int, durationSeconds: Int): POINT_TO_POINT_DOCUMENT = {
    new POINT_TO_POINT_DOCUMENT(route_ID, direction_ID, from_Point_ID, to_Point_ID, day_Type, observed_Time, durationSeconds, Weather.getCurrentRainfall)
  }

  def setLiveStreamCollection(enabled: Boolean) = {
    liveStreamCollectionEnabled = enabled
  }

  def setHistoricalDataStoring(enabled: Boolean) = {
    historicalDataStoringEnabled = enabled
  }


}
