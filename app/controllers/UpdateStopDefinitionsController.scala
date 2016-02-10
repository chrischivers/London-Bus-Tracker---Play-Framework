package controllers

import database.tfl.TFLInsertStopDefinition
import datadefinitions.tfl.TFLDefinitions
import datadefinitions.tfl.loadresources.LoadStopDefinitions
import play.api.mvc._


object UpdateStopDefinitionsController extends Controller {

  def updateStopDefinitions = Action {
    TFLDefinitions.updateStopDefinitionsFromWeb()
    Ok("started")
  }

  def getPercentageComplete = Action {
    Ok(LoadStopDefinitions.percentageComplete.toString)
  }

  def getNumberInserted = Action {
    Ok(TFLInsertStopDefinition.numberDBInsertsRequested.toString)
  }
  def getNumberUpdated = Action {
    Ok(TFLInsertStopDefinition.numberDBUpdatesRequested.toString)
  }

}
