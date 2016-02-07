package controllers.interfaces

import database.tfl.TFLInsertStopDefinition
import datadefinitions.tfl.TFLDefinitions
import datadefinitions.tfl.loadresources.LoadStopDefinitions

/**
 * User Control Interface for Updating Stop Definitions
 */
object UpdateStopDefinitionsControlInterface extends StartStopControlInterface {


    def getPercentageComplete = LoadStopDefinitions.percentageComplete
    def getNumberInserted = TFLInsertStopDefinition.numberDBInsertsRequested
    def getNumberUpdated = TFLInsertStopDefinition.numberDBUpdatesRequested


  override def stop(): Unit = throw new IllegalArgumentException("Unable to stop Update Stop Definitions From Web (will leave with incomplete data)")

  override def start(): Unit = {
    TFLDefinitions.updateStopDefinitionsFromWeb()
  }
}
