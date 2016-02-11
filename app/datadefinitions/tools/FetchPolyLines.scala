package datadefinitions.tools

import java.util.NoSuchElementException

import akka.actor.{ActorSystem, Actor, Props}
import com.mongodb.casbah.Imports
import database.{ROUTE_DEFINITION_DOCUMENT, POLYLINE_INDEX_DOCUMENT, ROUTE_DEFINITIONS_COLLECTION, POLYLINE_INDEX_COLLECTION}
import database.tfl.{TFLInsertUpdateRouteDefinition, TFLInsertPolyLineDefinition, TFLGetRouteDefinitionDocument, TFLGetPolyLineIndexDocument}
import datadefinitions.ResourceOperations
import datadefinitions.tfl.TFLDefinitions
import play.api.Logger

import scala.io.{BufferedSource, Source}

object FetchPolyLines extends ResourceOperations  {

  val TIME_BETWEEN_POLYLINE_QUERIES = 250
  var numberLinesProcessed = 0
  var numberPolyLinesUpdatedFromWeb = 0
  var numberPolyLinesUpdatedFromCache = 0

  var APICurrentIndex = 0

  var existingPolyLineIndex = {
    val cursor = TFLGetPolyLineIndexDocument.fetchAll()
    var polyLineIndexMap:Map[(String,String),String] = Map()
    for (doc <- cursor) {
      polyLineIndexMap += ((doc.get(POLYLINE_INDEX_COLLECTION.FROM_STOP_CODE).asInstanceOf[String],doc.get(POLYLINE_INDEX_COLLECTION.TO_STOP_CODE).asInstanceOf[String]) -> doc.get(POLYLINE_INDEX_COLLECTION.POLYLINE).asInstanceOf[String])
    }
    polyLineIndexMap
  }
  Logger.info("Polyline cache size: " + existingPolyLineIndex.size)


  def updateAll() = {
    val streamActor = actorResourcesSystem.actorOf(Props[UpdateAllPolyLinesActor], name = "AddPolyLinesActor")
    streamActor ! "updateAll"
  }

  def getPolyLineForTwoPoints(fromStopCode: String, toStopCode: String): String = {

    val thisStopCodeLat = TFLDefinitions.PointDefinitionsMap(toStopCode).latitude
    val thisStopCodeLng = TFLDefinitions.PointDefinitionsMap(toStopCode).longitude

    val lastStopCodeLat = TFLDefinitions.PointDefinitionsMap(fromStopCode).latitude
    val lastStopCodeLng = TFLDefinitions.PointDefinitionsMap(fromStopCode).longitude

    if (existingPolyLineIndex.contains((fromStopCode, toStopCode))) {
      val polyLine = existingPolyLineIndex((fromStopCode, toStopCode))
      numberPolyLinesUpdatedFromCache += 1
      polyLine

    }  else { //get it from online
      val url = "https://maps.googleapis.com/maps/api/directions/xml?origin=" + lastStopCodeLat + "," + lastStopCodeLng + "&destination=" + thisStopCodeLat + "," + thisStopCodeLng + "&key=" + getAPIKeys.get + "&mode=driving"
     // println("from : " + fromStopCode + ". To: " + toStopCode + "." + "Url: " + url)
      val s = Source.fromURL(url).getLines()
      for (line <- s) {
        if (line.contains("OVER_QUERY_LIMIT")) {
          APICurrentIndex += 1
          Logger.info("" +
            "Over limit - new API being selected. APi Index: " + APICurrentIndex)
          if (getAPIKeys.isDefined) return getPolyLineForTwoPoints(fromStopCode,toStopCode)
          else {
            throw new IllegalStateException("Out of API Keys. URL: " + url)
          }
        }
        else if (line.contains("<overview_polyline>")) {
          val pointsLine = s.next()
          val polyLine = pointsLine.substring(11, pointsLine.length - 9)
          numberPolyLinesUpdatedFromWeb += 1
          Thread.sleep(TIME_BETWEEN_POLYLINE_QUERIES)
          return polyLine

        }
      }
      Logger.error("Cannot get polyline between stops " + fromStopCode + " and " + toStopCode + ". URL: " + url)
      throw new IllegalStateException("Cannot get polyline between stops " + fromStopCode + " and " + toStopCode + ". URL: " + url)
    }

  }


  private def getAPIKeys:Option[String] = {
    val APIKeys = List(
      "AIzaSyD-9dP1VD-Ok9-oY1aXhSZZCYR5CRo-Jus",
      "AIzaSyDSEq-FMJhzFbQNIgK1JNQZuaLcPFV3oxw",
      "AIzaSyDcuDPhqrEoVPoLxoeeLpWwx07fYjFqSeM",
      "AIzaSyCHLODVvW1s20QhS_zyKEAYnlbvsC6Gu9w",
      "AIzaSyAj6_kBtnllfulkTG0aih6onOnf9Qm5cX0",
      "AIzaSyAcWJNih_q90XV4ufyoNWpzzEsMP1PoLz0",
    "AIzaSyAk7O0DzuX5S1kmsI948QHEMGK1kJPAafM",
    "AIzaSyCupO_iJ-uaNnvE8V9fKG4Aeo0Z4OobhDc",
      "AIzaSyAxBbetDC6UR596Okg4luf3vJVwB2-BDTc",
      "AIzaSyA-dJzMNsZwjlWulZOGmII-gSh8NaUd3kQ",
      "AIzaSyCj3EZ9527OuKIHlzJ3P9ycgUNQZe7xx4Y",
    "AIzaSyAGbM0iQ7EAvSKTbxtaZPXsGXX_qhyJRow",
    "AIzaSyD1WHQgCneuQluvW9AeO0-ZnEm1ZUD9BA0")
    if (APICurrentIndex < APIKeys.length) Some(APIKeys(APICurrentIndex)) else None
  }


  class UpdateAllPolyLinesActor extends Actor {


    override def receive: Receive = {
      case "updateAll" => updatePolyLines()
    }

    val collection = ROUTE_DEFINITIONS_COLLECTION

    def updatePolyLines() = {

      var docLastRead: Option[Imports.DBObject] = None
      val cursor = TFLGetRouteDefinitionDocument.fetchAllOrdered()
      for (doc <- cursor) {
        if (getAPIKeys.isDefined) {
          if (doc.get(collection.FIRST_LAST).asInstanceOf[String] == "FIRST") {
            docLastRead = Some(doc)
          } else {
            if (docLastRead.getOrElse(throw new IllegalStateException("Error. Last read document is None: " + doc)).get(collection.POLYLINE) == null) {
                //Only do if no polyline recorded
                //INSERT

                val thisStopCode = doc.get(collection.POINT_ID).asInstanceOf[String]
                val lastStopCode = docLastRead.get.get(collection.POINT_ID).asInstanceOf[String]
              try {
                val polyLine = getPolyLineForTwoPoints(lastStopCode, thisStopCode)
                addPolyLinetoIndexDBIfRequired(lastStopCode, thisStopCode, polyLine) //Updates previous with polyline route to next
                addPolyLineToRouteDefDB(docLastRead.get, polyLine)
              } catch {
                case e:NoSuchElementException => Logger.debug("No stop found for: " + lastStopCode + " or " + thisStopCode)
              }
              }
              if (doc.get(collection.FIRST_LAST).asInstanceOf[String] != "LAST") {
                docLastRead = Some(doc)
              } else {
                docLastRead = None
              }

            }
            numberLinesProcessed += 1
        } else {
          Logger.info("No more API keys available")
        }
      }
      Logger.info("Finished PolyLineProcessing")
    }



    private def addPolyLinetoIndexDBIfRequired(thisStopCode:String, nextStopCode:String, polyLine: String) = {
      if (!existingPolyLineIndex.contains(thisStopCode,nextStopCode)) {
        existingPolyLineIndex += (thisStopCode, nextStopCode) -> polyLine
        TFLInsertPolyLineDefinition.insertDoc(new POLYLINE_INDEX_DOCUMENT(thisStopCode, nextStopCode, polyLine))
      }
    }

    private def addPolyLineToRouteDefDB(doc:Imports.DBObject,polyLine:String) = {
      val routeID = doc.get(collection.ROUTE_ID).asInstanceOf[String]
      val direction = doc.get(collection.DIRECTION_ID).asInstanceOf[Int]
      val sequence = doc.get(collection.SEQUENCE).asInstanceOf[Int]
      val stopCode = doc.get(collection.POINT_ID).asInstanceOf[String]
      val firstLast = if (doc.get(collection.FIRST_LAST) == null) None else Some(doc.get(collection.FIRST_LAST).asInstanceOf[String])
      val routeDocToUpdate = new ROUTE_DEFINITION_DOCUMENT(routeID, direction, sequence, stopCode, firstLast)
      TFLInsertUpdateRouteDefinition.updateDocumentWithPolyLine(routeDocToUpdate, polyLine)

    }
  }


}

//