package database

import akka.actor.{Actor, ActorRef, Props}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.{MongoDBObject, Imports}
import database.BusRouteDefinitionsDB.BUS_ROUTE_DEFINITION_DOCUMENT
import database.BusRouteDefinitionsDB.BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE_DEFINITION
import datadefinitions.BusDefinitions._
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play.current


object BusRouteDefinitionsDB extends DatabaseCollections {

  case object BUS_ROUTE_DEFINITION_DOCUMENT {
    val ROUTE_ID = "ROUTE_ID"
    val DIRECTION = "DIRECTION"
    val BUS_STOP_SEQUENCE = "BUS_STOP_SEQUENCE"

    case object BUS_STOP_SEQUENCE_DEFINITION {
      val SEQUENCE_NO = "SEQUENCE_NO"
      val BUS_STOP_ID = "BUS_STOP_ID"
      val BUS_STOP_NAME = "BUS_STOP_NAME"
      val TOWARDS = "TOWARDS"
      val BUS_STOP_INDICATOR = "BUS_STOP_INDICATOR"
      val BUS_STOP_STATUS = "BUS_STOP_STATUS"
      val LONGITUDE = "LONGITUDE"
      val LATITUDE = "LATITUDE"
      val POLYLINE = "POLYLINE"
    }
  }

  override val supervisor: ActorRef = Akka.system.actorOf(Props[BusDefinitionsDBSupervisor], name = "BusDefinitionsDBSupervisor")

  def insertBusRouteDefinitionsIntoDB(busRouteDefinitions: BusRouteDefinitions) = {
    incrementLogRequest(IncrementNumberInsertsRequested(busRouteDefinitions.size))
    supervisor ! busRouteDefinitions
  }

  def insertBusRouteDefinitionsIntoDB(busRoute: BusRoute, sequenceList: List[BusStopInSequence]) = {
    incrementLogRequest(IncrementNumberInsertsRequested(1))
    supervisor !(busRoute, sequenceList)
  }

  def getBusRouteDefinitionsFromDB: BusRouteDefinitions = {
    incrementLogRequest(IncrementNumberGetRequests(1))


    val cursor = dBConnection.find()
    cursor.map(x => {
      BusRoute(
        x.getAs[String](BUS_ROUTE_DEFINITION_DOCUMENT.ROUTE_ID).get,
        x.getAs[String](BUS_ROUTE_DEFINITION_DOCUMENT.DIRECTION).get) ->
        x.getAs[List[DBObject]](BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE).get.map(y => {
          BusStopInSequence(
            y.getAs[Int](BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE_DEFINITION.SEQUENCE_NO).get,
            BusStop(
              y.getAs[String](BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE_DEFINITION.BUS_STOP_ID).get,
              y.getAs[String](BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE_DEFINITION.BUS_STOP_NAME).get,
              y.getAs[String](BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE_DEFINITION.TOWARDS).get,
              y.getAs[String](BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE_DEFINITION.BUS_STOP_INDICATOR).get,
              y.getAs[Boolean](BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE_DEFINITION.BUS_STOP_STATUS).get,
              y.getAs[String](BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE_DEFINITION.LATITUDE).get,
              y.getAs[String](BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE_DEFINITION.LONGITUDE).get
            ),
            y.getAs[String](BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE_DEFINITION.POLYLINE) match {
              case Some(str) => Some(str)
              case None => None
            }
          )
        })
    }) toMap
  }



  override val collectionName: String = "BusRouteDefinitions"
  override val fieldsVector = Vector(BUS_ROUTE_DEFINITION_DOCUMENT.ROUTE_ID, BUS_ROUTE_DEFINITION_DOCUMENT.DIRECTION, BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE)
  override val indexKeyList = List((BUS_ROUTE_DEFINITION_DOCUMENT.ROUTE_ID, 1), (BUS_ROUTE_DEFINITION_DOCUMENT.DIRECTION, 1))
  override val uniqueIndex = true
  override val dBConnection: MongoCollection = MongoDatabase.getCollection(this)
}

class BusDefinitionsDBSupervisor extends Actor {

  val busDefinitionsDBWorker: ActorRef = context.actorOf(Props[BusDefinitionsDBWorker], name = "BusDefinitionsDBWorker")

  override def receive: Actor.Receive = {
    case doc: BusRouteDefinitions => doc.foreach(singleRoute => busDefinitionsDBWorker ! singleRoute)
    case doc: (BusRoute, List[BusStopInSequence]) => busDefinitionsDBWorker ! doc
    case doc: IncrementNumberInsertsRequested =>  BusRouteDefinitionsDB.numberInsertsRequested += doc.incrementBy
    case doc: IncrementNumberInsertsCompleted =>  BusRouteDefinitionsDB.numberInsertsCompleted += doc.incrementBy
    case doc: IncrementNumberGetRequests =>  BusRouteDefinitionsDB.numberGetRequests += doc.incrementBy
    case _ =>
      Logger.error("BusDefinitionsDBSupervisor Actor received unknown message: ")
      throw new IllegalStateException("BusDefinitionsDBSupervisor received unknown message")
  }
}

class BusDefinitionsDBWorker extends Actor {


  override def receive: Receive = {
    case doc: (BusRoute, List[BusStopInSequence]) => insertToDB(doc._1, doc._2)
    case _ =>
      Logger.error("BusDefinitionsDBWorker Actor received unknown message")
      throw new IllegalStateException("BusDefinitionsDBWorker received unknown message")
  }


  private def insertToDB(busRoute: BusRoute, sequenceList: List[BusStopInSequence]) = {

    val stopSequenceList: List[Imports.DBObject] = sequenceList.map(seq => {
      MongoDBObject(
        BUS_STOP_SEQUENCE_DEFINITION.SEQUENCE_NO -> seq.sequenceNumber,
        BUS_STOP_SEQUENCE_DEFINITION.BUS_STOP_ID -> seq.busStop.busStopID,
        BUS_STOP_SEQUENCE_DEFINITION.BUS_STOP_NAME -> seq.busStop.busStopName,
        BUS_STOP_SEQUENCE_DEFINITION.TOWARDS -> seq.busStop.towards,
        BUS_STOP_SEQUENCE_DEFINITION.BUS_STOP_INDICATOR -> seq.busStop.busStopIndicator,
        BUS_STOP_SEQUENCE_DEFINITION.BUS_STOP_STATUS -> seq.busStop.busStopStatus,
        BUS_STOP_SEQUENCE_DEFINITION.LONGITUDE -> seq.busStop.longitude,
        BUS_STOP_SEQUENCE_DEFINITION.LATITUDE -> seq.busStop.latitude,
        BUS_STOP_SEQUENCE_DEFINITION.POLYLINE -> seq.polyLineToNextStop)

    })

    val newRouteDefDoc = MongoDBObject(
      BUS_ROUTE_DEFINITION_DOCUMENT.ROUTE_ID -> busRoute.routeID,
      BUS_ROUTE_DEFINITION_DOCUMENT.DIRECTION -> busRoute.direction,
      BUS_ROUTE_DEFINITION_DOCUMENT.BUS_STOP_SEQUENCE -> stopSequenceList)

    val query = MongoDBObject(
      BUS_ROUTE_DEFINITION_DOCUMENT.ROUTE_ID -> busRoute.routeID,
      BUS_ROUTE_DEFINITION_DOCUMENT.DIRECTION -> busRoute.direction
    )

    BusRouteDefinitionsDB.dBConnection.update(query, newRouteDefDoc, upsert = true)
    BusRouteDefinitionsDB.incrementLogRequest(IncrementNumberInsertsCompleted(1))
  }

}
