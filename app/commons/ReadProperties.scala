package commons

import scala.io.Source

/**
 * Created by chrischivers on 24/10/15.
 */
object ReadProperties {

  val propertiesFile = "properties.config"

  // Code lifted from http://stackoverflow.com/questions/17873162/how-to-read-properties-file-in-scala
  def getProperty(propertyName:String):String = {
    Source.fromFile(propertiesFile)
    .getLines()
    .find(_.startsWith(propertyName+"="))
    .map(_.replace(propertyName+"=",""))
    .getOrElse(throw new IllegalStateException("Cannot read from properties file. Property Name: " + propertyName))
  }

}
