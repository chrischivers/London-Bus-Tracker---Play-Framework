package controllers

import controllers.interfaces.{UpdateStopDefinitionsControlInterface, UpdateRouteDefinitionsControlInterface}
import play.api.mvc._


object UpdateStopDefinitionsController extends Controller {

  def updateStopDefinitions = Action {
    UpdateStopDefinitionsControlInterface.start()
    Ok("started")
  }

  def getPercentageComplete = Action {
    Ok(UpdateStopDefinitionsControlInterface.getPercentageComplete.toString)
  }

  def getNumberInserted = Action {
    Ok(UpdateStopDefinitionsControlInterface.getNumberInserted.toString)
  }
  def getNumberUpdated = Action {
    Ok(UpdateStopDefinitionsControlInterface.getNumberUpdated.toString)
  }

}
