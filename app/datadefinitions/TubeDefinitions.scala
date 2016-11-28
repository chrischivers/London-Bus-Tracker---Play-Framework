package datadefinitions

import database.PolyLineIndexDB.{PolyLine, PolyLineDefinition}
import database.{TubeRouteDefinitionsDB, PolyLineIndexDB}
import org.bson.json.JsonParseException
import play.api.Logger
import play.api.libs.json._

import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.xml.XML

object TubeDefinitions {

  type TubeRouteDefinitions = Map[TubeRoute, List[TubeBranchDefinition]]

  case class TubeRoute(routeID: String, direction: String)

  case class TubeStop(tubeStopID: String, tubeStopName: String, tubeStopStatus: Boolean, latitude: String, longitude: String)

  case class TubeBranchDefinition(branchName: String, tubeStopsInSequence: List[TubeStopInSequence])

  case class TubeStopInSequence(sequenceNumber: Int, tubeStop: TubeStop, polyLineToNextStop: Option[PolyLine])

  var APICurrentIndex = 0

  @volatile var numberRoutesStillToProcess: Long = 0

  val tubeRouteDefinitions: TubeRouteDefinitions = retrieveAllTubeRouteDefinitionsFromDB

  val sortedTubeRouteList: List[String] = tubeRouteDefinitions.keySet.map(key => key.routeID).toList.sorted

  /*  val sortedTubeRouteListWithFirstLastStops: List[(String, String, String)] = {
      sortedRouteList.map(route => (route, busRouteDefinitions.filter(x => x._1.routeID == route).head._2.head.busStop.busStopName, busRouteDefinitions.filter(x => x._1.routeID == route).last._2.head.busStop.busStopName))
    }
    */
  //TODO

  private def retrieveAllTubeRouteDefinitionsFromDB: TubeRouteDefinitions = TubeRouteDefinitionsDB.getTubeRouteDefinitionsFromDB

  private def persistTubeRouteDefinitionsToDB(tubeRouteDefinitions: TubeRouteDefinitions) = TubeRouteDefinitionsDB.insertTubeRouteDefinitionsIntoDB(tubeRouteDefinitions)

  private def persistTubeRouteDefinitionsToDB(tubeRoute: TubeRoute, branchDefinitionList: List[TubeBranchDefinition]) = TubeRouteDefinitionsDB.insertTubeRouteDefinitionsIntoDB(tubeRoute, branchDefinitionList)

  def refreshTubeRouteDefinitionFromWeb: Unit = {
    val allRoutesUrl = "https://api.tfl.gov.uk/Line/Route?app_id=06e150ca&app_key=004fb63b46743baa5411d3a05f482109"
    //    def getSingleRouteUrl(routeID: String, direction: String) = "https://api.tfl.gov.uk/Line/" + routeID + "/Route/Sequence/" + direction + "?excludeCrowding=True&app_id=06e150ca&app_key=004fb63b46743baa5411d3a05f482109"
    def getSingleRouteUrl(routeID: String, direction: String) = "https://api.tfl.gov.uk/Line/" + routeID + "/Route/Sequence/" + direction + "?excludeCrowding=True&app_id=06e150ca&app_key=d425d9ed4a43e1202d30fa688fda6686"

    val allRouteJsonDataRaw = Source.fromURL(allRoutesUrl).mkString
    val updatedRouteList = Json.parse(allRouteJsonDataRaw)
    val routeID = updatedRouteList \\ "id"
    val modeName = updatedRouteList \\ "modeName"
    val routeSection = updatedRouteList \\ "routeSections"
    val directions = routeSection.map(x => x \\ "direction").map(x => x.distinct)
    val allRoutes: Seq[((JsValue, JsValue), Seq[JsValue])] = routeID zip modeName zip directions
    val tubeRoutes: Seq[((JsValue, JsValue), Seq[JsValue])] = allRoutes.filter(x => x._1._2.as[String] == "tube") //.filter(x => x._1._1.as[String] == "3")
    numberRoutesStillToProcess = tubeRoutes.foldLeft(0)((acc, x) => acc + x._2.length)

    tubeRoutes.foreach(route => {
      route._2.foreach(direction => {
        try {
          val routeIDString = route._1._1.as[String]
          val directionString = direction.as[String]
          Logger.info("processing route " + routeIDString + ", direction " + directionString)
          val singleRouteJsonDataRaw = Source.fromURL(getSingleRouteUrl(routeIDString, directionString)).mkString
          val singleRouteJsonDataParsed = Json.parse(singleRouteJsonDataRaw)
          val tubeRouteBranches = singleRouteJsonDataParsed \\ "orderedLineRoutes"
          val stopPointSequenceList = (singleRouteJsonDataParsed \\ "stopPointSequences").head.as[List[JsValue]]
          val tubeStopListParsed: Set[TubeStop] = convertTubeStopJSONtoTubeStop(stopPointSequenceList.toList)

          val branchDefinitions:List[TubeBranchDefinition] = tubeRouteBranches.map(branch => {
            val name = branch \\ "name"
            val tubeStopIDSequence = (branch \\ "naptanIds").head.as[List[String]]
            val tubeStopSequence = tubeStopIDSequence.map(id => {
              tubeStopListParsed.find(stop => stop.tubeStopID == id).getOrElse(throw new IllegalStateException("Tube Station with ID " + id + " not found in tubeStopList"))
            }).toList
            val tubeStopPolyLineList = addSequenceNoAndPolyLinesToTubeStopList(tubeStopSequence)
            TubeBranchDefinition(name.head.as[String], tubeStopPolyLineList)
          }).toList
          val tubeRoute = TubeRoute(routeIDString, directionString)

          persistTubeRouteDefinitionsToDB(tubeRoute, branchDefinitions)
        } catch {
          case e: NoSuchElementException => Logger.info("No Such Element Exception for route: " + route._1._1.as[String].toUpperCase + ", and direction: " + direction.as[String])
          case e: JsonParseException => Logger.info("JSON parse exception for route: " + route._1._1.as[String].toUpperCase + ", and direction: " + direction.as[String] + ". " + e.printStackTrace())
        }
        numberRoutesStillToProcess -= 1
      })

    })

    def convertTubeStopJSONtoTubeStop(stopPointSequenceList: List[JsValue]): Set[TubeStop] = {
      stopPointSequenceList.flatMap(sequence => {
        val stopPoints = (sequence \\ "stopPoint").head.as[List[JsValue]]
        stopPoints.map(point => {
          val id = (point \\ "id").headOption match {
            case Some(jsVal) => jsVal.as[String]
            case None => ""
          }
          val name = (point \\ "name").headOption match {
            case Some(jsVal) => jsVal.as[String]
            case None => ""
          }
          val status = (point \\ "status").headOption match {
            case Some(jsVal) => jsVal.as[Boolean]
            case None => true
          }
          val latitude = (point \\ "lat").headOption.getOrElse(throw new IllegalArgumentException("No Stop latitude value found in record")).as[BigDecimal]
          val longitude = (point \\ "lon").headOption.getOrElse(throw new IllegalArgumentException("No Stop longitude value found in record")).as[BigDecimal]
          TubeStop(id, name, status, latitude.toString(), longitude.toString())
        })
      }) toSet
      }


    def addSequenceNoAndPolyLinesToTubeStopList(tubeStopList: List[TubeStop]): List[TubeStopInSequence] = {
      var tubeStopPolyLineList: ListBuffer[TubeStopInSequence] = ListBuffer()

      for (i <- 0 until tubeStopList.length - 1) {
        tubeStopPolyLineList += TubeStopInSequence(i, tubeStopList(i), getPolyLineToNextTubeStop(tubeStopList(i), tubeStopList(i + 1)))
      }
      tubeStopPolyLineList += TubeStopInSequence(tubeStopList.length - 1, tubeStopList.last, None)
      tubeStopPolyLineList.toList
    }

    def getPolyLineToNextTubeStop(fromStop: TubeStop, toStop: TubeStop): Option[PolyLine] = {

      val TIME_BETWEEN_POLYLINE_QUERIES = 250

      def getPolyLineFromDb(fromStop: TubeStop, toStop: TubeStop): Option[PolyLine] = PolyLineIndexDB.getPolyLineFromDB(fromStop.tubeStopID, toStop.tubeStopID)

      def persistPolyLineDefinitionToDB(polyLineDef: PolyLineDefinition) = PolyLineIndexDB.insertPolyLineIndexDocument(polyLineDef)

      def getPolyLineFromWeb(fromStop: TubeStop, toStop: TubeStop): Option[PolyLine] = {

        Thread.sleep(TIME_BETWEEN_POLYLINE_QUERIES)

        def getPolyLineUrl(fromStop: TubeStop, toStop: TubeStop) = "https://maps.googleapis.com/maps/api/directions/xml?origin=" + fromStop.latitude.toString + "," + fromStop.longitude.toString + "&destination=" + toStop.latitude.toString + "," + toStop.longitude.toString + "&key=" + getAPIKeys.get + "&mode=driving"

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
          case Some(polyLine) => persistPolyLineDefinitionToDB(PolyLineDefinition(fromStop.tubeStopID, toStop.tubeStopID, polyLine))
            Some(polyLine)
          case None => None
        }
      }
    }
    Logger.info("Tube Route Definitions update complete")
  }
}