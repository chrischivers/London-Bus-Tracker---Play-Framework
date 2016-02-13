package datadefinitions.tfl.loadresources

import java.io.IOException

import akka.actor.{Actor, Props}
import database.{STOP_DEFINITION_DOCUMENT, STOP_DEFINITIONS_COLLECTION}
import database.tfl.{TFLInsertStopDefinition, TFLGetStopDefinitionDocument}
import datadefinitions.ResourceOperations
import datadefinitions.tfl.StopDefinitionFields
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play.current

import scala.io.Source


object LoadStopDefinitions extends ResourceOperations{

  var percentageComplete = 0

  private val collection = STOP_DEFINITIONS_COLLECTION


  // Maps StopCode -> (StopPointName;StopPointType;Towards;Bearing;StopPointIndicator;StopPointState;Latitude;Longitude)
  private var stopDefinitionMap: Map[String, StopDefinitionFields] = Map()

  def getStopDefinitionMap: Map[String, StopDefinitionFields]  = {
    if (stopDefinitionMap.isEmpty) {
      retrieveFromDB()
      stopDefinitionMap
    } else stopDefinitionMap
  }


  private def retrieveFromDB(): Unit = {
    var tempMap: Map[String, StopDefinitionFields] = Map()

    val cursor = TFLGetStopDefinitionDocument.fetchAll()
    for (doc <- cursor) {
      val stopCode = doc.get(collection.STOP_CODE).asInstanceOf[String]
      val stopName = doc.get(collection.STOP_NAME).asInstanceOf[String]
      val stopType = doc.get(collection.STOP_TYPE).asInstanceOf[String]
      val towards = doc.get(collection.TOWARDS).asInstanceOf[String]
      val bearing = doc.get(collection.BEARING).asInstanceOf[Int]
      val indicator = doc.get(collection.INDICATOR).asInstanceOf[String]
      val state = doc.get(collection.STATE).asInstanceOf[Int]
      val lat = doc.get(collection.LAT).asInstanceOf[String]
      val lng = doc.get(collection.LNG).asInstanceOf[String]

      tempMap += (stopCode -> new StopDefinitionFields(stopName, stopType, towards, bearing, indicator, state, lat, lng))
    }
    stopDefinitionMap = tempMap
    Logger.info("Number stop definitions fetched from DB: " + stopDefinitionMap.size)
  }

  def updateFromWeb() = {
    val streamActor = Akka.system.actorOf(Props[UpdateStopDefinitionsFromWeb], name = "UpdateStopeDefinitionsFromWeb")
    streamActor ! "start"
  }

  class UpdateStopDefinitionsFromWeb extends Actor {

    override def receive: Receive = {
      case "start" => updateFromWeb()
    }

  def updateFromWeb() = {

    lazy val stopList: Set[String] = TFLGetStopDefinitionDocument.getDistinctStopCodes
    val totalNumberOfStops = stopList.size
    Logger.info("Number of stops: " + stopList.size)
    var numberLinesProcessed = 0
    var tempMap: Map[String, StopDefinitionFields] = Map()
    def tflURL(stopCode: String): String = "http://countdown.api.tfl.gov.uk/interfaces/ura/instant_V1?StopCode1=" + stopCode + "&ReturnList=StopPointName,StopPointType,Towards,Bearing,StopPointIndicator,StopPointState,Latitude,Longitude"

    Logger.info("Loading Stop Definitions From Web...")

    stopList.foreach { x =>
      try {
        val s = Source.fromURL(tflURL(x))
        s.getLines().drop(1).foreach(line => {
          val split = splitLine(line)
          val lat = BigDecimal(split(6)).toString()
          val lng = BigDecimal(split(7)).toString()
          tempMap += (x -> new StopDefinitionFields(split(0), split(1), split(2), split(3).toInt, split(4), split(5).toInt, lat, lng))
          numberLinesProcessed += 1
          percentageComplete = ((numberLinesProcessed.toDouble / totalNumberOfStops.toDouble) * 100).toInt
        }
        )
      } catch {
        case ioe:IOException => Logger.info("No stop information for stop " + x + ". Moving on...")//Skip and move on
      }
    }
    percentageComplete = 100
    Logger.info("Stop definitons from web loaded")
    stopDefinitionMap = tempMap
    persistToDB()
  }

  private def splitLine(line: String) = line
    .substring(1, line.length - 1) // remove leading and trailing square brackets,
    .split(",(?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)") // split at commas ignoring if in quotes (regEx taken from StackOverflow question: http://stackoverflow.com/questions/13335651/scala-split-string-by-commnas-ignoring-commas-between-quotes)
    .map(x => x.replaceAll("\"", "")) //take out double quotations
    .map(x => x.replaceAll(";", ",")) //take out semicolons (causes errors on read of file)
    .tail // discards the first element (always '1')


  private def persistToDB() = {

    stopDefinitionMap.foreach {
      case ((stop_code), sdf: StopDefinitionFields) =>
        val newDoc = new STOP_DEFINITION_DOCUMENT(stop_code, sdf.stopPointName, sdf.stopPointType, sdf.towards, sdf.bearing, sdf.stopPointIndicator, sdf.stopPointState, sdf.latitude, sdf.longitude)
        TFLInsertStopDefinition.insertDoc(newDoc)
    }
    Logger.info("Stop Definitons loaded from web and persisted to DB")

  }

}

}


