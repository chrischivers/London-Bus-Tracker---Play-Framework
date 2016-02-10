package controllers

import database.tfl.TFLInsertUpdateRouteDefinition
import datadefinitions.tfl.TFLDefinitions
import datadefinitions.tfl.loadresources.LoadRouteDefinitions
import play.api.Play.current
import play.api.mvc._


object UpdateRouteDefinitionsController extends Controller {

  def updateRouteDefinitions = Action {
    TFLDefinitions.updateRouteDefinitionsFromWeb()
    Ok("started")
  }

  def getPercentageComplete = Action {
    Ok(LoadRouteDefinitions.percentageComplete.toString)
  }

  def getNumberInserted = Action {
    Ok(TFLInsertUpdateRouteDefinition.numberDBInsertsRequested.toString)
  }
  def getNumberUpdated = Action {
    Ok(TFLInsertUpdateRouteDefinition.numberDBUpdatesRequested.toString)
  }

}

