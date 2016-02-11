package controllers

import play.api.Logger
import play.api.mvc._
import processes.tfl.TFLIterateOverArrivalStreamSupervisor
import processes.weather.Weather

object StreamProcessorController extends Controller {

  val mb = 1024*1024
  val runtime = Runtime.getRuntime
  var started = false


  def isStarted = Action {
    Ok(started.toString)
  }

  def getNumberLinesRead = Action {
    Ok(TFLIterateOverArrivalStreamSupervisor.numberProcessed.toString)
  }

  def getNumberLinesSinceRestart = Action {
    Ok(TFLIterateOverArrivalStreamSupervisor.numberProcessedSinceRestart.toString)
  }

  def getCurrentRainFall = Action {
    Ok(Weather.getCurrentRainfall.toString)
  }

  def getMemoryState = Action {
    Ok(((runtime.totalMemory - runtime.freeMemory) / mb) + "," +
      (runtime.freeMemory / mb) + "," +
      (runtime.totalMemory / mb) + "," +
      (runtime.maxMemory / mb))
  }


  def startStreamProcessor = Action {
    if (!started) {
      started = true
      Logger.info("Starting Stream Processor")
      TFLIterateOverArrivalStreamSupervisor.start()
    }
    Ok("started")
  }

  def stopStreamProcessor = Action {
    if (started) {
      started = false
      Logger.info("Stopping Stream Processor")
      TFLIterateOverArrivalStreamSupervisor.stop()
    }
    Ok("stopped")
  }
}
