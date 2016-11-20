package datadefinitions

import database.{BusRouteDefinitionsDB, PolyLineIndexDB}
import org.bson.json.JsonParseException
import play.api.libs.json._
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.xml.XML
import play.api.Logger

object BusDefinitions {

  type BusRouteDefinitions = Map[BusRoute, List[BusStopInSequence]]

  case class BusRoute(routeID: String, direction: String)

  case class BusStop(busStopID: String, busStopName: String, towards: String, busStopIndicator: String, busStopStatus: Boolean, latitude: String, longitude: String)

  case class BusStopInSequence(sequenceNumber: Int, busStop: BusStop, polyLineToNextStop: Option[BusPolyLine])

  type BusPolyLine = String

  case class PolyLineDefinition(from: BusStop, to: BusStop, polyline: BusPolyLine)

  var APICurrentIndex = 0

  @volatile var numberRoutesStillToProcess: Long = 0

  val busRouteDefinitions: BusRouteDefinitions = retrieveAllBusRouteDefinitionsFromDB

  val sortedRouteList: List[String] = {
    val list = busRouteDefinitions.keySet.map(key => key.routeID) toList
    val partitionedList = list.partition(x => x.forall(_.isDigit))
    val sortedIntList = partitionedList._1.map(_.toInt).sorted
    val sortedStringList = partitionedList._2.sorted
    sortedIntList.map(_.toString) ++ sortedStringList
  }

  val sortedRouteListWithFirstLastStops: List[(String, String, String)] = {
    sortedRouteList.map(route => (route, busRouteDefinitions.filter(x => x._1.routeID == route).head._2.head.busStop.busStopName, busRouteDefinitions.filter(x => x._1.routeID == route).last._2.head.busStop.busStopName))
  }

  private def retrieveAllBusRouteDefinitionsFromDB: BusRouteDefinitions = BusRouteDefinitionsDB.getBusRouteDefinitionsFromDB

  private def persistBusRouteDefinitionsToDB(busRouteDefinitions: BusRouteDefinitions) = BusRouteDefinitionsDB.insertRouteDefinitionsIntoDB(busRouteDefinitions)

  private def persistBusRouteDefinitionsToDB(busRoute: BusRoute, sequenceList: List[BusStopInSequence]) = BusRouteDefinitionsDB.insertRouteDefinitionsIntoDB(busRoute, sequenceList)

  def refreshBusRouteDefinitionFromWeb(updateNewRoutesOnly: Boolean): Unit = {
    val allRoutesUrl = "https://api.tfl.gov.uk/Line/Route?app_id=06e150ca&app_key=004fb63b46743baa5411d3a05f482109"
//    def getSingleRouteUrl(routeID: String, direction: String) = "https://api.tfl.gov.uk/Line/" + routeID + "/Route/Sequence/" + direction + "?excludeCrowding=True&app_id=06e150ca&app_key=004fb63b46743baa5411d3a05f482109"
    def getSingleRouteUrl(routeID: String, direction: String) = "https://api.tfl.gov.uk/Line/" + routeID + "/Route/Sequence/" + direction + "?excludeCrowding=True&app_id=06e150ca&app_key=d425d9ed4a43e1202d30fa688fda6686"

    val allRouteJsonDataRaw = Source.fromURL(allRoutesUrl).mkString
    val updatedRouteList = Json.parse(allRouteJsonDataRaw)
    val routeID = updatedRouteList \\ "id"
    val modeName = updatedRouteList \\ "modeName"
    val routeSection = updatedRouteList \\ "routeSections"
    val directions = routeSection.map(x => x \\ "direction")
    val allRoutes: Seq[((JsValue, JsValue), Seq[JsValue])] = routeID zip modeName zip directions
    val busRoutes: Seq[((JsValue, JsValue), Seq[JsValue])] = allRoutes.filter(x => x._1._2.as[String] == "bus") //.filter(x => x._1._1.as[String] == "3")
    numberRoutesStillToProcess = busRoutes.foldLeft(0)((acc, x) => acc + x._2.length)

    busRoutes.foreach(route => {
      route._2.foreach(direction => {
        try {
          val routeIDString = route._1._1.as[String].toUpperCase
          val directionString = direction.as[String]
          if(busRouteDefinitions.get(BusRoute(routeIDString, directionString)).isDefined && updateNewRoutesOnly) {
            Logger.info("skipping route " + routeIDString + "and direction " + directionString + " as already in DB")
          } else {
            Logger.info("processing route " + routeIDString + ", direction " + directionString)
            val singleRouteJsonDataRaw = Source.fromURL(getSingleRouteUrl(routeIDString, directionString)).mkString
            val singleRouteJsonDataParsed = Json.parse(singleRouteJsonDataRaw)
            val busStopSequences = singleRouteJsonDataParsed \\ "stopPointSequences"
            val busStopSequence = (busStopSequences.head \\ "stopPoint").head.as[List[JsValue]]
            val busStopList = convertBusStopSequenceToBusStopList(busStopSequence)
            val busStopPolyLineList = addSequenceNoAndPolyLinesToBusStopList(busStopList)

            val busRoute = BusRoute(routeIDString, directionString)
            persistBusRouteDefinitionsToDB(busRoute, busStopPolyLineList)
          }
        } catch {
          case e: NoSuchElementException => Logger.info("No Such Element Exception for route: " + route._1._1.as[String].toUpperCase + ", and direction: " + direction.as[String])
          case e: JsonParseException => Logger.info("JSON parse exception for route: " + route._1._1.as[String].toUpperCase + ", and direction: " + direction.as[String] + ". " + e.printStackTrace())
        }
        numberRoutesStillToProcess -= 1
      })

    })

    def convertBusStopSequenceToBusStopList(busStopSequence: List[JsValue]): List[BusStop] = {
      busStopSequence.map(busStop => {
        val towards = (busStop \\ "towards").headOption match {
          case Some(jsVal) => jsVal.as[String]
          case None => ""
        }
        val stopLetter = (busStop \\ "stopLetter").headOption match {
          case Some(jsVal) => jsVal.as[String]
          case None => ""
        }
        val status = (busStop \\ "status").headOption match {
          case Some(jsVal) => jsVal.as[Boolean]
          case None => true
        }
        val id = (busStop \\ "id").headOption.getOrElse(throw new IllegalArgumentException("No Stop ID value found in record")).as[String]
        val stopName = (busStop \\ "name").headOption match {
          case Some(jsVal) => jsVal.as[String]
          case None => "No Stop Name"
        }
        val latitude = (busStop \\ "lat").headOption.getOrElse(throw new IllegalArgumentException("No Stop latitude value found in record")).as[BigDecimal]
        val longitude = (busStop \\ "lon").headOption.getOrElse(throw new IllegalArgumentException("No Stop longitude value found in record")).as[BigDecimal]
        new BusStop(id, stopName, towards, stopLetter, status, latitude.toString(), longitude.toString())

      })
    }


    def addSequenceNoAndPolyLinesToBusStopList(busStopList: List[BusStop]): List[BusStopInSequence] = {
      var busStopPolyLineList: ListBuffer[BusStopInSequence] = ListBuffer()

      for (i <- 0 until busStopList.length - 1) {
        busStopPolyLineList += BusStopInSequence(i, busStopList(i), getPolyLineToNextStop(busStopList(i), busStopList(i + 1)))
      }
      busStopPolyLineList += BusStopInSequence(busStopList.length - 1, busStopList.last, None)
      busStopPolyLineList.toList
    }

    def getPolyLineToNextStop(fromStop: BusStop, toStop: BusStop): Option[BusPolyLine] = {

      val TIME_BETWEEN_POLYLINE_QUERIES = 250


      def getPolyLineFromDb(fromStop: BusStop, toStop: BusStop): Option[BusPolyLine] = PolyLineIndexDB.getPolyLineFromDB(fromStop, toStop)

      def persistPolyLineDefinitionToDB(polyLineDef: PolyLineDefinition) = PolyLineIndexDB.insertPolyLineIndexDocument(polyLineDef)

      def getPolyLineFromWeb(fromStop: BusStop, toStop: BusStop): Option[BusPolyLine] = {

        Thread.sleep(TIME_BETWEEN_POLYLINE_QUERIES)

        def getPolyLineUrl(fromStop: BusStop, toStop: BusStop) = "https://maps.googleapis.com/maps/api/directions/xml?origin=" + fromStop.latitude.toString + "," + fromStop.longitude.toString + "&destination=" + toStop.latitude.toString + "," + toStop.longitude.toString + "&key=" + getAPIKeys.get + "&mode=driving"

        def getAPIKeys: Option[String] = {
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

        val xml = XML.load(getPolyLineUrl(fromStop, toStop))
        (xml \\ "status").text match {
          case "OK" => val polyLine = (xml \\ "overview_polyline" \\ "points").text
            if (polyLine.length > 0) Some(polyLine) else None
          case "OVER_QUERY_LIMIT" => APICurrentIndex += 1
            if (getAPIKeys.isDefined) getPolyLineFromWeb(fromStop, toStop)
            else throw new IllegalStateException("Out of API Keys. URL: " + getPolyLineUrl(fromStop, toStop))
          case _ => throw new IllegalStateException("Unknown error retrieving polyline from URL " + getPolyLineUrl(fromStop, toStop))
        }

      }
      getPolyLineFromDb(fromStop, toStop) match {
        case Some(polyLine) => Some(polyLine)
        case None => getPolyLineFromWeb(fromStop, toStop) match {
          case Some(polyLine) => persistPolyLineDefinitionToDB(PolyLineDefinition(fromStop, toStop, polyLine))
            Some(polyLine)
          case None => None
        }
      }
    }
    //persistBusRouteDefinitionsToDB(busRouteDefinitions)
    Logger.info("Bus Route Definitions update complete")
  }
}