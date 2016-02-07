package controllers

import controllers.interfaces.{AddPolyLinesControlInterface, UpdateStopDefinitionsControlInterface}
import play.api.mvc._


object AddPolyLinesController extends Controller {

  def addPolyLines = Action {
    AddPolyLinesControlInterface.start()
    Ok("started")
  }

  def getLinesRead = Action {
    Ok(AddPolyLinesControlInterface.getNumberLinesRead.toString)
  }

  def getNumberUpdatedFromWeb = Action {
    Ok(AddPolyLinesControlInterface.getNumberPolyLinesUpdatedFromWeb.toString)
  }
  def getNumberUpdatedFromCache = Action {
    Ok(AddPolyLinesControlInterface.getNumberPolyLinesUpdatedFromCache.toString)
  }

}
