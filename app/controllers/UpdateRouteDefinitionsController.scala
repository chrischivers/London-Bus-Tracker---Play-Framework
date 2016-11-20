package controllers

import database.BusRouteDefinitionsDB
import datadefinitions.BusDefinitions
import play.api.mvc._


object UpdateRouteDefinitionsController extends Controller {

  def updateRouteDefinitionsAll = Action {
    BusDefinitions.refreshBusRouteDefinitionFromWeb(updateNewRoutesOnly = false)
    Ok("started")
  }

  def updateRouteDefinitionsNewMissing = Action {
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

