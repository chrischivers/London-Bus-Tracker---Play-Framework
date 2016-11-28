package controllers

import database.{TubeRouteDefinitionsDB, BusRouteDefinitionsDB}
import datadefinitions.{TubeDefinitions, BusDefinitions}
import play.api.mvc._


object UpdateTubeRouteDefinitionsController extends Controller {

  def updateTubeRouteDefinitionsAll = Action {
    TubeDefinitions.refreshTubeRouteDefinitionFromWeb
    Ok("started")
  }

  def getNumberRoutesStillToUpdate = Action {
    Ok(TubeDefinitions.numberRoutesStillToProcess.toString)
  }

  def getNumberDBInsertsRequested = Action {
    Ok(TubeRouteDefinitionsDB.numberInsertsRequested.toString)
  }
  def getNumberDBInsertsCompleted = Action {
    Ok(TubeRouteDefinitionsDB.numberInsertsCompleted.toString)
  }

}

