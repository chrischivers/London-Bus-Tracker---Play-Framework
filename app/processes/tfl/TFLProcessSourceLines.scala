package processes.tfl

import database.{InsertRouteSectionRecord, RouteSectionHistoryDB}
import datadefinitions.BusDefinitions
import datadefinitions.BusDefinitions.BusRoute
import datasource.SourceLine
import commons.Commons._
import processes.weather.Weather
import streaming.LiveStreamingCoordinatorImpl



object TFLProcessSourceLines {


  val MAXIMUM_AGE_OF_RECORDS_IN_HOLDING_BUFFER = 600000 //In Ms
  var numberNonMatches = 0
  var nonMatches:Set[BusRoute] = Set()


  /**
   * The holding buffer holds the temporary list of routes awaiting the next stop.
   * It is a Map of (BusRoute, VehicleReg) -> (Stop ID, ArrivalTimeStamp)
   */
  private var holdingBuffer: Map[(BusRoute, String), (String, Long)] = Map()
  private var liveStreamCollectionEnabled: Boolean = false
  private var historicalDataStoringEnabled = false

  //private val stopIgnoreList = TFLDefinitions.StopIgnoreList
  //private val routeIgnoreList = TFLDefinitions.RouteIgnoreList
 // private val publicHolidayList = TFLDefinitions.PublicHolidayList

  def getBufferSize: Int = holdingBuffer.size

  def apply(newLine: SourceLine) {

    if (validateLine(newLine)) {
      // Send to Live Streaming Coordinator if Enabled
      if (liveStreamCollectionEnabled) LiveStreamingCoordinatorImpl.processSourceLine(newLine)

      // Process for historical data if Enabled
      if (historicalDataStoringEnabled && !isPublicHoliday(newLine)) processLineForHistoricalData(newLine)
    }
  }

  /**
   * Processes the validated line for historical data by comparing with holdingBuffer and, if necessary, inserting into the DB
   * @param newLine The validated Source Line
   */
  def processLineForHistoricalData(newLine: SourceLine) = {
    if (!holdingBuffer.contains(newLine.busRoute, newLine.busRegistration)) {
      updateHoldingBufferAndPrune(newLine)
    } else {
      val existingValues = holdingBuffer(newLine.busRoute, newLine.busRegistration)
      val existingStopID = existingValues._1
      val existingArrivalTimeStamp = existingValues._2
      val existingPointSequenceNo = BusDefinitions.busRouteDefinitions(newLine.busRoute).filter(x => x.busStop.busStopID == existingStopID).head.sequenceNumber
      val newPointSequenceNo = BusDefinitions.busRouteDefinitions(newLine.busRoute).filter(x => x.busStop.busStopID == newLine.busStopID).last.sequenceNumber
      if (newPointSequenceNo == existingPointSequenceNo + 1) {
        val durationInSeconds = ((newLine.arrival_TimeStamp - existingArrivalTimeStamp) / 1000).toInt
        if (durationInSeconds > 0) {
          RouteSectionHistoryDB.insertRouteSectionHistoryIntoDB(InsertRouteSectionRecord(newLine.busRoute, existingStopID, newLine.busStopID, existingArrivalTimeStamp.getDayCode, existingArrivalTimeStamp.getTimeOffset, durationInSeconds, Weather.getCurrentRainfall))
          updateHoldingBufferAndPrune(newLine)
        } else {
          updateHoldingBufferAndPrune(newLine) // Replace existing values with new values
        }
      } else if (newPointSequenceNo >= existingPointSequenceNo) {
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
  private def updateHoldingBufferAndPrune(line: SourceLine) = {
    if (!isFinalStop(line)) {
      holdingBuffer += ((line.busRoute, line.busRegistration) ->(line.busStopID, line.arrival_TimeStamp))
      val CUT_OFF: Long = System.currentTimeMillis() - MAXIMUM_AGE_OF_RECORDS_IN_HOLDING_BUFFER
      holdingBuffer = holdingBuffer.filter { case ((_), (_, time)) => time > CUT_OFF }
    } else {
      holdingBuffer -= ((line.busRoute, line.busRegistration))
    }
  }

  /**
   * Checks the line is acceptable for inserting
   * @param line The source line
   * @return True if valid, false if not
   */
  private def validateLine(line: SourceLine): Boolean = {

    def inDefinitionFile(line: SourceLine): Boolean = {
      if (BusDefinitions.busRouteDefinitions.get(line.busRoute).isEmpty) {
        //println("non match on: " + line.busRoute)
        numberNonMatches += 1
        nonMatches += line.busRoute
        //logger.info("Cannot get definition. Line: " + line) //TODO Fix this
        false
      } else if (!BusDefinitions.busRouteDefinitions(line.busRoute).exists(x => x.busStop.busStopID == line.busStopID)) {
        numberNonMatches += 1
        // println(line.route_ID + "," + line.direction_ID + ":    " + TFLDefinitions.RouteDefinitionMap(line.route_ID, line.direction_ID))
        // println("Non Match: " + line.route_ID + ", " + line.direction_ID + ", " + line.stop_Code)
        false
      }
      else true
    }

    def isWithinTimeThreshold(line: SourceLine): Boolean = {
      ((line.arrival_TimeStamp - System.currentTimeMillis) / 1000) <= TFLProcessVariables.LINE_TOLERANCE_IN_RELATION_TO_CURRENT_TIME
    }

    def isNotOnIgnoreLists(line: SourceLine): Boolean = {
     // if (routeIgnoreList.contains(line.route_ID) || stopIgnoreList.contains(line.busStopID)) false else true
      true
    }

    if (!isNotOnIgnoreLists(line)) return false
    if (!inDefinitionFile(line)) return false
    if (!isWithinTimeThreshold(line)) return false
    true
  }

  private def isFinalStop(line: SourceLine): Boolean = BusDefinitions.busRouteDefinitions(line.busRoute).last.busStop.busStopID == line.busStopID

  private def isPublicHoliday(line: SourceLine): Boolean = {

  /*  val date = new Date(line.arrival_TimeStamp)
    for (pubHolDate <- publicHolidayList) if (DateUtils.isSameDay(date, pubHolDate)) return true*/
    false
  }

  def setLiveStreamCollection(enabled: Boolean) = {
    liveStreamCollectionEnabled = enabled
  }

  def setHistoricalDataStoring(enabled: Boolean) = {
    historicalDataStoringEnabled = enabled
  }


}
