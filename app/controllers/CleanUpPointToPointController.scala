package controllers

import datadefinitions.tools.CleanPointToPointData
import play.api.mvc._


object CleanUpPointToPointController extends Controller {

  def cleanUpPointToPoint = Action {
    CleanPointToPointData.start()
    Ok("started")
  }

  def getNumberDocumentsRead = Action {
    Ok(CleanPointToPointData.numberDocumentsRead.toString)
  }

  def getNumberDocumentsDeleted = Action {
    Ok(CleanPointToPointData.numberDocumentsDeleted.toString)
  }

}


