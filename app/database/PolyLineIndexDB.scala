package database

import akka.actor.{Actor, ActorRef, Props}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import database.PolyLineIndexDB.{PolyLineDefinition, POLYLINE_DOCUMENT}
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play.current



object PolyLineIndexDB extends DatabaseCollections {

  type PolyLine = String
  case class PolyLineDefinition(fromStopID: String, toStopID: String, polyline: PolyLine)

  case object POLYLINE_DOCUMENT {
    val FROM_STOP_ID = "FROM_STOP_ID"
    val TO_STOP_ID = "TO_STOP_ID"
    val POLYLINE = "POLYLINE"
  }

  override val supervisor: ActorRef = Akka.system.actorOf(Props[PolyLineIndexDBSupervisor], name = "PolyLineIndexDBSupervisor")

  def insertPolyLineIndexDocument(polyLineDefinition: PolyLineDefinition) = {
    incrementLogRequest(IncrementNumberInsertsRequested(1))
    supervisor ! polyLineDefinition
  }

  def getPolyLineFromDB(fromID:String, toID:String):Option[PolyLine] = {
    incrementLogRequest(IncrementNumberGetRequests(1))

    val query = MongoDBObject(
    POLYLINE_DOCUMENT.FROM_STOP_ID -> fromID,
    POLYLINE_DOCUMENT.TO_STOP_ID -> toID)

    val cursor = dBConnection.find(query)
    if(cursor.hasNext) {
      val polyLine = Some(cursor.next().getAs[String](POLYLINE_DOCUMENT.POLYLINE).get)
      if (polyLine.get == "null") None else polyLine
    } else None
  }


  override val collectionName: String = "PolyLineIndex"
  override val fieldsVector = Vector(POLYLINE_DOCUMENT.FROM_STOP_ID, POLYLINE_DOCUMENT.TO_STOP_ID, POLYLINE_DOCUMENT.POLYLINE)
  override val indexKeyList = List((POLYLINE_DOCUMENT.FROM_STOP_ID, 1),(POLYLINE_DOCUMENT.TO_STOP_ID, 1))
  override val uniqueIndex = true
  override val dBConnection: MongoCollection = MongoDatabase.getCollection(this)

}

class PolyLineIndexDBSupervisor extends Actor {

  val polyLineIndexDBWorker: ActorRef = context.actorOf(Props[PolyLineIndexDBWorker], name = "PolyLineIndexDBWorker")

  override def receive: Actor.Receive = {
    case doc: PolyLineDefinition => polyLineIndexDBWorker ! doc
    case doc: IncrementNumberInsertsRequested =>  PolyLineIndexDB.numberInsertsRequested += doc.incrementBy
    case doc: IncrementNumberInsertsCompleted =>  PolyLineIndexDB.numberInsertsCompleted += doc.incrementBy
    case doc: IncrementNumberGetRequests =>  PolyLineIndexDB.numberGetRequests += doc.incrementBy
    case _ =>
      Logger.error("PolyLineIndexDBSupervisor Actor received unknown message")
      throw new IllegalStateException("PolyLineIndexDBSupervisor Actor received unknown message")
  }
}

class PolyLineIndexDBWorker extends Actor {


  override def receive: Receive = {
    case doc: PolyLineDefinition => insertToDB(doc)
    case _ =>
      Logger.error("PolyLineIndexDBWorker Actor received unknown message")
      throw new IllegalStateException("PolyLineIndexDBWorker Actor received unknown message")
  }


  private def insertToDB(polyLineDefinition: PolyLineDefinition) = {

  val query = MongoDBObject(
      POLYLINE_DOCUMENT.FROM_STOP_ID -> polyLineDefinition.fromStopID,
      POLYLINE_DOCUMENT.TO_STOP_ID -> polyLineDefinition.toStopID)

    val update = $set(POLYLINE_DOCUMENT.POLYLINE -> polyLineDefinition.polyline)

    PolyLineIndexDB.dBConnection.update(query, update, upsert = true)
    PolyLineIndexDB.incrementLogRequest(IncrementNumberInsertsCompleted(1))
  }

}
