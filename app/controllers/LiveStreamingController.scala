package controllers


import controllers.interfaces.LiveStreamingControlInterface
import play.api.mvc._
import play.api.Play.current
import streaming.{WebSocketSupervisor, WebSocketActor}


object LiveStreamingController extends Controller {

  def socket = WebSocket.acceptWithActor[String, String] { request => out =>
  WebSocketSupervisor.props(out)
}

  def isStarted = Action {
    Ok(LiveStreamingControlInterface.started.toString)
  }


  def turnOnLiveStreaming = Action {
    LiveStreamingControlInterface.start()
    Ok("started")
  }

  def turnOffLiveStreaming = Action {
    LiveStreamingControlInterface.stop()
    Ok("stopped")
  }

  def getNumberLiveActors = Action {
    Ok(LiveStreamingControlInterface.getNumberLiveActors.toString)
  }

  def getNumberLiveChildren = Action {
    Ok(LiveStreamingControlInterface.getNumberLiveChildren.toString)
  }


}
