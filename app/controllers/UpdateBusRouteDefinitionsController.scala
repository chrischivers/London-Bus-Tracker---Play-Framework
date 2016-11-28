package controllers

import database.BusRouteDefinitionsDB
import datadefinitions.{TubeDefinitions, BusDefinitions}
import play.api.mvc._


object UpdateBusRouteDefinitionsController extends Controller {

  def updateBusRouteDefinitionsAll = Action {
    BusDefinitions.refreshBusRouteDefinitionFromWeb(updateNewRoutesOnly = false)
    Ok("started")
  }

  def updateBusRouteDefinitionsNewMissing = Action {
    BusDefinitions.refreshBusRouteDefinitionFromWeb(updateNewRoutesOnly = true)
    Ok("started")
  }

  def getNumberRoutesStillToUpdate = Action {
    Ok(BusDefinitions.numberRoutesStillToProcess.toString)
  }

  def getNumberDBInsertsRequested = Action {
    Ok(BusRouteDefinitionsDB.numberInsertsRequested.toString)
  }
  def getNumberDBInsertsCompleted = Action {
    Ok(BusRouteDefinitionsDB.numberInsertsCompleted.toString)
  }

}

