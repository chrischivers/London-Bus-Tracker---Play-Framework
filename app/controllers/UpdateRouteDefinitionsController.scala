package controllers

import controllers.interfaces.{UpdateRouteDefinitionsControlInterface, LiveStreamingControlInterface}
import play.api.Play.current
import play.api.mvc._
import streaming.MyWebSocketActor


object UpdateRouteDefinitionsController extends Controller {

  def updateRouteDefinitions = Action {
    UpdateRouteDefinitionsControlInterface.start()
    Ok("started")
  }

  def getPercentageComplete = Action {
    Ok(UpdateRouteDefinitionsControlInterface.getPercentageComplete.toString)
  }

  def getNumberInserted = Action {
    Ok(UpdateRouteDefinitionsControlInterface.getNumberInserted.toString)
  }
  def getNumberUpdated = Action {
    Ok(UpdateRouteDefinitionsControlInterface.getNumberUpdated.toString)
  }

}
