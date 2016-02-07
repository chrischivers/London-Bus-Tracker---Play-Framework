package controllers.interfaces

import datadefinitions.tfl.TFLDefinitions
import datadefinitions.tools.FetchPolyLines

/**
 * User Control Interface for the Adding Polylines Function
 */
object AddPolyLinesControlInterface extends StartStopControlInterface {

  /**
   * Gets the variable array for displaying on the User Interface
   * @return An array of the variables to display on the user interface
   */

    def getNumberLinesRead = FetchPolyLines.numberLinesProcessed
    def getNumberPolyLinesUpdatedFromWeb = FetchPolyLines.numberPolyLinesUpdatedFromWeb
    def getNumberPolyLinesUpdatedFromCache = FetchPolyLines.numberPolyLinesUpdatedFromCache


  override def stop(): Unit = throw new IllegalStateException("Unable to stop Add PolyLines (will leave with incomplete data)")

  override def start(): Unit = {
    TFLDefinitions.addPolyLinesFromWeb()
  }
}


