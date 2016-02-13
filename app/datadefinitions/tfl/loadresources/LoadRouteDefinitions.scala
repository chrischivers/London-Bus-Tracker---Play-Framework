package datadefinitions.tfl.loadresources

import akka.actor.{Actor, Props}
import database.{ROUTE_DEFINITION_DOCUMENT, ROUTE_DEFINITIONS_COLLECTION}
import database.tfl.{TFLInsertUpdateRouteDefinition, TFLGetRouteDefinitionDocument}
import datadefinitions.LoadResourceFromSource
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play.current

import scala.io.{BufferedSource, Source}

object LoadRouteDefinitions extends LoadResourceFromSource {

  override val bufferedSource: BufferedSource = DEFAULT_ROUTE_DEF_FILE

  var percentageComplete = 0
  private val collection = ROUTE_DEFINITIONS_COLLECTION
  //Route definition Map (RouteID Direction -> List of Point Sequence, Stop Code, FirstLast, PolyLine
  private var routeDefinitionMap: Map[(String, Int), List[(Int, String, Option[String], String)]] = Map()
  private val routesToFetchByHTML = getRoutesToFetchByHtml


  def getRouteDefinitionMap: Map[(String, Int), List[(Int, String, Option[String], String)]] = {
    if (routeDefinitionMap.isEmpty) {
      retrieveFromDB()
      routeDefinitionMap
    } else routeDefinitionMap
  }

  private def retrieveFromDB() = {
    var tempMap: Map[(String, Int), List[(Int, String, Option[String], String)]] = Map()

    val cursor = TFLGetRouteDefinitionDocument.fetchAll()
    for (doc <- cursor) {
      val routeID = doc.get(collection.ROUTE_ID).asInstanceOf[String]
      val direction = doc.get(collection.DIRECTION_ID).asInstanceOf[Int]
      val sequence = doc.get(collection.SEQUENCE).asInstanceOf[Int]
      val stop_code = doc.get(collection.POINT_ID).asInstanceOf[String]
      val firstLast = doc.get(collection.FIRST_LAST).asInstanceOf[String]
      val polyLine = doc.get(collection.POLYLINE).asInstanceOf[String]
      val firstLastOption = Option(firstLast)

      val listFromTempMap = tempMap.get(routeID, direction)
      if (listFromTempMap.isDefined) {
        val list = listFromTempMap.get
        val combinedList: List[(Int, String, Option[String], String)] = list ++ List((sequence, stop_code, firstLastOption, polyLine))
        tempMap += ((routeID, direction) -> combinedList.sortBy(_._1))
      } else {
        tempMap += ((routeID, direction) -> List((sequence, stop_code, firstLastOption, polyLine)))
      }
    }
    routeDefinitionMap = tempMap
    Logger.info("Number route definitions fetched from DB: " + routeDefinitionMap.size)
  }

  def updateFromWeb() = {
    val streamActor = Akka.system.actorOf(Props[UpdateRouteDefinitionsFromWeb], name = "UpdateRouteDefinitionsFromWeb")
    streamActor ! "start"
  }


  class UpdateRouteDefinitionsFromWeb extends Actor {

    override def receive: Receive = {
      case "start" => updateFromWebFile()
    }


    def updateFromWebFile() = {
      Logger.info("Loading Route Definitions From CSV file...")
      var tempMap: Map[(String, Int), Map[Int, (String, Option[String], String)]] = Map()

      var numberLinesProcessed = 0

      var prevRecord: (String, Int) = ("0", 0)

      bufferedSource.getLines().drop(1).foreach((line) => {
        try {
          val splitLine = line.split(",")
          val route_ID = splitLine(0)
          val direction = splitLine(1).toInt
          val sequence = splitLine(2).toInt
          val stopID = splitLine(4)

          // If the route is a HAIL AND RIDE, fetch using HTML METHOD
          if (!routesToFetchByHTML.contains(route_ID) && List(1,2).contains(direction)) {

            var firstLast: Option[String] = None

            if (sequence == 1) {
              firstLast = Some("FIRST")
              if (prevRecord !=("0", 0)) {
                val previousMap = tempMap(prevRecord)
                val lastOfPrevious = previousMap.maxBy(_._1)
                val modifiedlastOfPrevious = (lastOfPrevious._1, (lastOfPrevious._2._1, Some("LAST"), lastOfPrevious._2._3))
                val modifiedPreviousMap = previousMap + modifiedlastOfPrevious
                tempMap += prevRecord -> modifiedPreviousMap
              }
            }

            val currentMapOption = tempMap.get(route_ID, direction)
            if (currentMapOption.isDefined) {
              var currentMap = currentMapOption.get
              currentMap += sequence ->(stopID, firstLast, "")
              tempMap += (route_ID, direction) -> currentMap
            } else {
              tempMap += (route_ID, direction) -> Map(sequence ->(stopID, firstLast, ""))
            }

            prevRecord = (route_ID, direction)

            numberLinesProcessed += 1
          }
        }

        catch {
          case e: ArrayIndexOutOfBoundsException => if (line != "\u001A") {
            Logger.error("Error reading route list file. Error on line: " + line)
            throw new ArrayIndexOutOfBoundsException("Error reading route list file. Error on line: " + line)
          }
          case nfe: NumberFormatException => Logger.debug("number format exception for line: " + line + ". Moving on...")
        }
      })


      Logger.info("Route Definitions from CSV loaded")
      val mapforHTMLFetches = fetchUsingHTMLMethod()
      tempMap = tempMap ++ mapforHTMLFetches
      routeDefinitionMap = tempMap.map { case ((route, dir), mapValue) => ((route, dir), mapValue.map { case (seq, (stopCode, firstLast, polyLine)) => (seq, stopCode, firstLast, polyLine) }.toList.sortBy(_._1)) }
      persistToDB()
      percentageComplete = 100
    }


    private def fetchUsingHTMLMethod(): Map[(String, Int), Map[Int, (String, Option[String], String)]] = {


      var tempMap: Map[(String, Int), Map[Int, (String, Option[String], String)]] = Map()


      routesToFetchByHTML.foreach { case (route, webRouteID) =>

        for (direction <- 1 to 2) {
          var stopCodeSequenceMap: Map[Int, (String, Option[String], String)] = Map()

          val tflURL: String = if (direction == 1) {
            "http://m.countdown.tfl.gov.uk/showJourneyPattern/" + webRouteID + "/Outbound"
          } else if (direction == 2) {
            "http://m.countdown.tfl.gov.uk/showJourneyPattern/" + webRouteID + "/Back"
          } else {
            ""
          }

          if (tflURL != "") {
            val s = Source.fromURL(tflURL)
            var pointSequence = 1
            var skipThisRoute = false

            s.getLines().foreach((line) => {
              // For some routes there are mutliple pattern segments (e.g. route 134). This discards the first one if a second one follows (only second is kept)
              if (line.contains("route segment icon") && line.contains(";From")) {
                pointSequence = 1
                stopCodeSequenceMap = Map()
              }

              if (line.contains("route segment icon") && line.contains(";Via")) {
                skipThisRoute = true
              }

              if (line.contains("route segment icon") && line.contains(";To")) {
                skipThisRoute = true
              }

              if (line.contains("<dd><a href=")) {
                val startChar: Int = line.indexOf("searchTerm=") + 11
                val endChar: Int = line.indexOf("+")
                val stopCode = line.substring(startChar, endChar)
                val first_last: Option[String] = {
                  if (pointSequence == 1) Some("FIRST") else None
                }

                stopCodeSequenceMap += pointSequence ->(stopCode, first_last, "")
                pointSequence += 1
              }
            })
            if (stopCodeSequenceMap.nonEmpty) {
              // Set LAST on last option
              val last: (Int, (String, Option[String], String)) = {
                val lastOne = stopCodeSequenceMap.maxBy(_._1)
                val lastOneMap = lastOne._1 ->(lastOne._2._1, Some("LAST"), lastOne._2._3)
                lastOneMap
              }
              stopCodeSequenceMap = stopCodeSequenceMap + last
            } else skipThisRoute = true
            if (!skipThisRoute) tempMap += (route, direction) -> stopCodeSequenceMap
            else {
              //  println("Skipped: " + webRouteID + ", " + direction)
              None
            }
          }
        }
      }
      tempMap
    }
  }

  private def getRoutesToFetchByHtml: Map[String, String] = {
    val routeListFile = DEFAULT_LOAD_USING_HTML_METHOD_FILE
    var routeToWebRouteMap: Map[String, String] = Map()
    routeListFile.getLines().drop(1).foreach((line) => {
      val splitLine = line.split(",")
      routeToWebRouteMap += splitLine(0) -> splitLine(1)
    })
    routeToWebRouteMap
  }


  private def persistToDB(): Unit = {

    routeDefinitionMap.foreach {
      case ((routeID, direction), list) =>
        list.foreach {
          case (sequence, stopCode, firstLast, polyLine) =>
            val newDoc = new ROUTE_DEFINITION_DOCUMENT(routeID, direction, sequence, stopCode, firstLast)
            TFLInsertUpdateRouteDefinition.insertDoc(newDoc)
        }
    }
    Logger.info("Route Definitons loaded from web and persisted to DB")
  }
/*
  private def updateFromWebOLDMETHOD() = {

    var tempMap: Map[(String, Int), List[(Int, String, Option[String], String)]] = Map()


    println("Loading Route Definitions From Web...")

    // RouteName, WebCode
    val routeListFile  = DEFAULT_ROUTE_LIST_FILE
    val numberLinesInFile = routeListFile.getLines().size
    println("Lines in file: " + numberLinesInFile)
    var numberLinesProcessed = 0


    routeListFile.getLines().drop(1).foreach((line) => {
      //drop first row and iterate through others
      try {
        val splitLine = line.split(",")
        val route_ID = splitLine(0)
        val route_Web_ID = splitLine(1)
        for (direction <- 1 to 2) {
          val stopListFromWeb = getStopList(route_Web_ID, direction)
          if (stopListFromWeb.isDefined) {
            stopListFromWeb.get.foreach {
              case (stopCode, pointSeq, first_last) =>

                val listFromTempMap = tempMap.get(route_ID, direction)
                if (listFromTempMap.isDefined) {
                  val list = listFromTempMap.get
                  val combinedList = list ++ List((pointSeq, stopCode, first_last, ""))
                  tempMap += ((route_ID, direction) -> combinedList.sortBy(_._1))
                } else {
                  tempMap += ((route_ID, direction) -> List((pointSeq, stopCode, first_last, "")))
                }
            }
          }
        }
        numberLinesProcessed += 1
        percentageComplete = ((numberLinesProcessed.toDouble / numberLinesInFile.toDouble) * 100).toInt
      }
      catch {
        case e: ArrayIndexOutOfBoundsException => throw new Exception("Error reading route list file. Error on line: " + line)
      }
    })

    percentageComplete = 100
    println("Route Definitions from web loaded")
    routeDefinitionMap = tempMap
    persistToDB()
  }


  private def getStopList(webRouteID: String, direction: Int): Option[List[(String, Int, Option[String])]] = {

    var stopCodeSequenceList: List[(String, Int, Option[String])] = List()

    val tflURL: String = if (direction == 1) {
      "http://m.countdown.tfl.gov.uk/showJourneyPattern/" + webRouteID + "/Outbound"
    } else if (direction == 2) {
      "http://m.countdown.tfl.gov.uk/showJourneyPattern/" + webRouteID + "/Back"
    } else {
      throw new IllegalStateException("Invalid direction ID")
    }

    val s = Source.fromURL(tflURL)
    var pointSequence = 1
    var skipThisRoute = false

    s.getLines().foreach((line) => {
      // For some routes there are mutliple pattern segments (e.g. route 134). This discards the first one if a second one follows (only second is kept)
      if (line.contains("route segment icon") && line.contains(";From")) {
        pointSequence = 1
        stopCodeSequenceList = List()
      }

      if (line.contains("route segment icon") && line.contains(";Via")) {
        skipThisRoute = true
      }

      if (line.contains("route segment icon") && line.contains(";To")) {
        skipThisRoute = true
      }

      if (line.contains("<dd><a href=")) {
        val startChar: Int = line.indexOf("searchTerm=") + 11
        val endChar: Int = line.indexOf("+")
        val stopCode = line.substring(startChar, endChar)
        val first_last: Option[String] = {
          if (pointSequence == 1) Some("FIRST") else None
        }

        stopCodeSequenceList = stopCodeSequenceList :+ ((stopCode, pointSequence, first_last))
        pointSequence += 1
      }
    })

    // Set LAST on last option
    val lastone: List[(String, Int, Option[String])] = stopCodeSequenceList.takeRight(1).map { case (x, y, z) => (x, y, Some("LAST")) }
    stopCodeSequenceList = stopCodeSequenceList.dropRight(1) ::: lastone
    if (!skipThisRoute) Some(stopCodeSequenceList) else {
      //  println("Skipped: " + webRouteID + ", " + direction)
      None
    }
  }*/

}
