package controllers

import controllers.interfaces.StreamProcessingControlInterface
import play.api.mvc._

object StreamProcessorController extends Controller {

  def isStarted = Action {
    Ok(StreamProcessingControlInterface.started.toString)
  }

  def getNumberLinesRead = Action {
    Ok(StreamProcessingControlInterface.getNumberLinesRead.toString)
  }

  def getNumberLinesSinceRestart = Action {
    Ok(StreamProcessingControlInterface.getNumberReadSinceRestart.toString)
  }

  def getCurrentRainFall = Action {
    Ok(StreamProcessingControlInterface.getCurrentRainfall.toString)
  }

  def getMemoryState = Action {
    Ok(StreamProcessingControlInterface.getUsedMemory + "," +
      StreamProcessingControlInterface.getFreeMemory + "," +
      StreamProcessingControlInterface.getTotalMemory + "," +
      StreamProcessingControlInterface.getMaxMemory)
  }


  def startStreamProcessor = Action {
    StreamProcessingControlInterface.start()
    Ok("started")
  }

  def stopStreamProcessor = Action {
    StreamProcessingControlInterface.stop()
    Ok("stopped")
  }



}
