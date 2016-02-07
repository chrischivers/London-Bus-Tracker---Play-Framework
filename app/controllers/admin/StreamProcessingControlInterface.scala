package controllers.admin

import play.api.Logger
import processes.tfl.TFLIterateOverArrivalStreamSupervisor
import processes.weather.Weather

/**
 * User Control Interface for Stream Processing Control
 */
object StreamProcessingControlInterface extends StartStopControlInterface {

  val mb = 1024*1024
  val runtime = Runtime.getRuntime

  val linesNotBeingReadAlertText = "LINES NOT BEING READ AS EXPECTED. POSSIBLE SERVER CRASH."
  val freeMemoryLowAlertText = "FREE MEMORY RUNNING LOW"
  val periodToCheck = 600000
  val MIN_LINES_TOREAD_IN_PERIOD = 10
  var timeStampLastChecked:Long = 0
  var linesReadOnLastCheck:Long = 0


  override def start(): Unit = {
    println("Starting Stream Processor")
    TFLIterateOverArrivalStreamSupervisor.start()

  }

  override def stop(): Unit = {
    println("Stopping Stream Processor")
    TFLIterateOverArrivalStreamSupervisor.stop()
  }

  def getNumberLinesRead = TFLIterateOverArrivalStreamSupervisor.numberProcessed.toString
  def getNumberReadSinceRestart = TFLIterateOverArrivalStreamSupervisor.numberProcessedSinceRestart.toString
  def getCurrentRainfall = Weather.getCurrentRainfall.toString
  def getUsedMemory = ((runtime.totalMemory - runtime.freeMemory) / mb).toString
  def getFreeMemory =  (runtime.freeMemory / mb).toString
  def getTotalMemory = (runtime.totalMemory / mb).toString
  def getMaxMemory =  (runtime.maxMemory / mb).toString


//TODO this needs to be incorporated elsewhere
  def checkAndSendForEmailAlerting(variableArray: Array[String]): Unit = {
    val linesRead = variableArray(0).toLong
    if (System.currentTimeMillis() - periodToCheck > timeStampLastChecked) {
      if (linesRead - linesReadOnLastCheck < MIN_LINES_TOREAD_IN_PERIOD && linesRead != 0) {
        Logger.error("No lines being read")
        EmailAlertInterface.sendAlert(linesNotBeingReadAlertText)
      }
      timeStampLastChecked = System.currentTimeMillis()
      linesReadOnLastCheck = linesRead
    }
    if (variableArray(6).toDouble - variableArray(3).toDouble < 200) {
      EmailAlertInterface.sendAlert(freeMemoryLowAlertText)
    }
  }
}
