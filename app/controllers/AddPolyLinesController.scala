package controllers

import controllers.interfaces.AddPolyLinesControlInterface
import datadefinitions.tfl.TFLDefinitions
import datadefinitions.tools.FetchPolyLines
import play.api.mvc._


object AddPolyLinesController extends Controller {

  def addPolyLines = Action {
    TFLDefinitions.addPolyLinesFromWeb()
    Ok("started")
  }

  def getLinesRead = Action {
    Ok(FetchPolyLines.numberLinesProcessed.toString)
  }

  def getNumberUpdatedFromWeb = Action {
    Ok( FetchPolyLines.numberPolyLinesUpdatedFromWeb.toString)
  }
  def getNumberUpdatedFromCache = Action {
    Ok(FetchPolyLines.numberPolyLinesUpdatedFromCache.toString)
  }
}