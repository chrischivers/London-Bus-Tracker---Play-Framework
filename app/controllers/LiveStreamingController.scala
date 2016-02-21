package controllers

import play.api.Logger

import streaming.{AtmosphereSupervisor, LiveStreamingCoordinatorImpl}

import scala.concurrent.duration._
import play.api.mvc._
import play.api.Play.current


object LiveStreamingController extends Controller {

  var started = false

  /*def websocket = WebSocket.acceptWithActor[String, String] { request => out =>
  WebSocketSupervisor.props(out)
}*/

def isStarted = Action {
    Ok(started.toString)
  }


  def turnOnLiveStreaming = Action {
    if (!started) {
      started = true
      Logger.info("Live Streaming turned on")
      LiveStreamingCoordinatorImpl.start()
    }
    Ok("started")
  }

  def turnOffLiveStreaming = Action {
    if (started) {
      started = false
      Logger.info("Live Streaming turned off")
      LiveStreamingCoordinatorImpl.stop()
    }
    Ok("stopped")
  }

  def getNumberLiveActors = Action {
    Ok(LiveStreamingCoordinatorImpl.getNumberLiveActors.toString)
  }

  def getNumberLiveChildren = Action {
    Ok(LiveStreamingCoordinatorImpl.getNumberLiveChildren.toString)
  }

  def getNumberConnections = Action {
    Ok(AtmosphereSupervisor.connectedActors.size.toString)
  }



}
