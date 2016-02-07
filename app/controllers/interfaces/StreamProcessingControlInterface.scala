package controllers.interfaces

import play.api.Logger
import processes.tfl.TFLIterateOverArrivalStreamSupervisor
import processes.weather.Weather

/**
 * User Control Interface for Stream Processing Control
 */
object StreamProcessingControlInterface extends StartStopControlInterface {

  val mb = 1024*1024
  val runtime = Runtime.getRuntime


  override def start(): Unit = {
    if (!started) {
      started = true
      println("Starting Stream Processor")
      TFLIterateOverArrivalStreamSupervisor.start()
    }
  }

  override def stop(): Unit = {
    if (started) {
      started = false
      println("Stopping Stream Processor")
      TFLIterateOverArrivalStreamSupervisor.stop()
    }
  }

  def getNumberLinesRead = TFLIterateOverArrivalStreamSupervisor.numberProcessed
  def getNumberReadSinceRestart = TFLIterateOverArrivalStreamSupervisor.numberProcessedSinceRestart
  def getCurrentRainfall = Weather.getCurrentRainfall
  def getUsedMemory = ((runtime.totalMemory - runtime.freeMemory) / mb)
  def getFreeMemory =  (runtime.freeMemory / mb)
  def getTotalMemory = (runtime.totalMemory / mb)
  def getMaxMemory =  (runtime.maxMemory / mb)
}
