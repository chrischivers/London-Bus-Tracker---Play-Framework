package controllers

import controllers.admin.StreamProcessingControlInterface
import play.api.mvc._

object StreamProcessorController extends Controller {

  def admin = Action {
    Ok(views.html.control())
  }

  def getNumberLinesRead = Action {
    Ok(StreamProcessingControlInterface.getNumberLinesRead)
  }

  def getNumberLinesSinceRestart = Action {
    Ok(StreamProcessingControlInterface.getNumberReadSinceRestart)
  }

  def getCurrentRainFall = Action {
    Ok(StreamProcessingControlInterface.getCurrentRainfall)
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
