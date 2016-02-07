package controllers.interfaces

import database.tfl.TFLInsertUpdateRouteDefinition
import datadefinitions.tfl.TFLDefinitions
import datadefinitions.tfl.loadresources.LoadRouteDefinitions


/**
 * User Control Interface for Updating Route Definition
 */
object UpdateRouteDefinitionsControlInterface extends StartStopControlInterface {

    def getPercentageComplete = LoadRouteDefinitions.percentageComplete
    def getNumberInserted = TFLInsertUpdateRouteDefinition.numberDBInsertsRequested
    def getNumberUpdated = TFLInsertUpdateRouteDefinition.numberDBUpdatesRequested


  override def stop(): Unit = throw new IllegalArgumentException("Unable to stop Update Route Definitions From Web (will leave with incomplete data)")

  override def start(): Unit = {
      TFLDefinitions.updateRouteDefinitionsFromWeb()
  }
}
