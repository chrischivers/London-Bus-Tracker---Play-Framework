package database

import akka.actor.{Actor, ActorRef, Props}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.{MongoDBObject, Imports}
import database.TubeRouteDefinitionsDB.TUBE_ROUTE_DEFINITION_DOCUMENT
import database.TubeRouteDefinitionsDB.TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCH_DOCUMENT
import database.TubeRouteDefinitionsDB.TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCH_DOCUMENT.TUBE_STOP_SEQUENCE_DOCUMENT
import datadefinitions.TubeDefinitions._
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play.current


object TubeRouteDefinitionsDB extends DatabaseCollections {

  case object TUBE_ROUTE_DEFINITION_DOCUMENT {
    val ROUTE_ID = "ROUTE_ID"
    val DIRECTION = "DIRECTION"
    val TUBE_ROUTE_BRANCHES = "TUBE_ROUTE_BRANCHES"

    case object TUBE_ROUTE_BRANCH_DOCUMENT {
      val BRANCH_NAME = "BRANCH_NAME"
      val TUBE_STOP_SEQUENCE = "TUBE_STOP_SEQUENCE"

      case object TUBE_STOP_SEQUENCE_DOCUMENT {
        val SEQUENCE_NO = "SEQUENCE_NO"
        val TUBE_STOP_ID = "TUBE_STOP_ID"
        val TUBE_STOP_NAME = "TUBE_STOP_NAME"
        val TUBE_STOP_STATUS = "TUBE_STOP_STATUS"
        val LONGITUDE = "LONGITUDE"
        val LATITUDE = "LATITUDE"
        val POLYLINE = "POLYLINE"
      }

    }
  }

  override val supervisor: ActorRef = Akka.system.actorOf(Props[TubeDefinitionsDBSupervisor], name = "TubeDefinitionsDBSupervisor")

  def insertTubeRouteDefinitionsIntoDB(tubeRouteDefinitions: TubeRouteDefinitions) = {
    incrementLogRequest(IncrementNumberInsertsRequested(tubeRouteDefinitions.size))
    supervisor ! tubeRouteDefinitions
  }

  def insertTubeRouteDefinitionsIntoDB(tubeRoute: TubeRoute, sequenceList: List[TubeBranchDefinition]) = {
    incrementLogRequest(IncrementNumberInsertsRequested(1))
    supervisor !(tubeRoute, sequenceList)
  }

  def getTubeRouteDefinitionsFromDB: TubeRouteDefinitions = {
    incrementLogRequest(IncrementNumberGetRequests(1))


    val cursor = dBConnection.find()
    cursor.map(x => {
      TubeRoute(
        x.getAs[String](TUBE_ROUTE_DEFINITION_DOCUMENT.ROUTE_ID).get,
        x.getAs[String](TUBE_ROUTE_DEFINITION_DOCUMENT.DIRECTION).get) ->
        x.getAs[List[DBObject]](TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCHES).get.map(y => {
          TubeBranchDefinition(
            y.getAs[String](TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCH_DOCUMENT.BRANCH_NAME).get,
            y.getAs[List[DBObject]](TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCH_DOCUMENT.TUBE_STOP_SEQUENCE).get.map(z => {
              TubeStopInSequence(
              z.getAs[Int](TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCH_DOCUMENT.TUBE_STOP_SEQUENCE_DOCUMENT.SEQUENCE_NO).get,
              TubeStop(
                z.getAs[String](TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCH_DOCUMENT.TUBE_STOP_SEQUENCE_DOCUMENT.TUBE_STOP_ID).get,
                z.getAs[String](TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCH_DOCUMENT.TUBE_STOP_SEQUENCE_DOCUMENT.TUBE_STOP_NAME).get,
                z.getAs[Boolean](TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCH_DOCUMENT.TUBE_STOP_SEQUENCE_DOCUMENT.TUBE_STOP_STATUS).get,
                z.getAs[String](TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCH_DOCUMENT.TUBE_STOP_SEQUENCE_DOCUMENT.LATITUDE).get,
                z.getAs[String](TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCH_DOCUMENT.TUBE_STOP_SEQUENCE_DOCUMENT.LONGITUDE).get
              ),
              z.getAs[String](TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCH_DOCUMENT.TUBE_STOP_SEQUENCE_DOCUMENT.POLYLINE) match {
                case Some(str) => Some(str)
                case None => None
              })
            }))
        })
    }) toMap
  }



  override val collectionName: String = "TubeRouteDefinitions"
  override val fieldsVector = Vector(TUBE_ROUTE_DEFINITION_DOCUMENT.ROUTE_ID, TUBE_ROUTE_DEFINITION_DOCUMENT.DIRECTION, TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCHES)
  override val indexKeyList = List((TUBE_ROUTE_DEFINITION_DOCUMENT.ROUTE_ID, 1), (TUBE_ROUTE_DEFINITION_DOCUMENT.DIRECTION, 1))
  override val uniqueIndex = true
  override val dBConnection: MongoCollection = MongoDatabase.getCollection(this)
}

class TubeDefinitionsDBSupervisor extends Actor {

  val tubeDefinitionsDBWorker: ActorRef = context.actorOf(Props[TubeDefinitionsDBWorker], name = "TubeDefinitionsDBWorker")

  override def receive: Actor.Receive = {
    case doc: TubeRouteDefinitions => doc.foreach(singleRoute => tubeDefinitionsDBWorker ! singleRoute)
    case doc: (TubeRoute, List[TubeBranchDefinition]) => tubeDefinitionsDBWorker ! doc
    case doc: IncrementNumberInsertsRequested =>  TubeRouteDefinitionsDB.numberInsertsRequested += doc.incrementBy
    case doc: IncrementNumberInsertsCompleted =>  TubeRouteDefinitionsDB.numberInsertsCompleted += doc.incrementBy
    case doc: IncrementNumberGetRequests =>  TubeRouteDefinitionsDB.numberGetRequests += doc.incrementBy
    case _ =>
      Logger.error("TubeDefinitionsDBSupervisor Actor received unknown message: ")
      throw new IllegalStateException("TubeDefinitionsDBSupervisor received unknown message")
  }
}

class TubeDefinitionsDBWorker extends Actor {


  override def receive: Receive = {
    case doc: (TubeRoute, List[TubeBranchDefinition]) => insertToDB(doc._1, doc._2)
    case _ =>
      Logger.error("TubeDefinitionsDBWorker Actor received unknown message")
      throw new IllegalStateException("TubeDefinitionsDBWorker received unknown message")
  }


  private def insertToDB(tubeRoute: TubeRoute, branchList: List[TubeBranchDefinition]) = {

    val branchStopSequenceList: List[Imports.DBObject] = branchList.map(branch => {
      MongoDBObject(
        TUBE_ROUTE_BRANCH_DOCUMENT.BRANCH_NAME -> branch.branchName,
      TUBE_ROUTE_BRANCH_DOCUMENT.TUBE_STOP_SEQUENCE ->
      branch.tubeStopsInSequence.map(seq => {
        MongoDBObject(
          TUBE_STOP_SEQUENCE_DOCUMENT.SEQUENCE_NO -> seq.sequenceNumber,
          TUBE_STOP_SEQUENCE_DOCUMENT.TUBE_STOP_ID -> seq.tubeStop.tubeStopID,
          TUBE_STOP_SEQUENCE_DOCUMENT.TUBE_STOP_NAME -> seq.tubeStop.tubeStopName,
          TUBE_STOP_SEQUENCE_DOCUMENT.TUBE_STOP_STATUS -> seq.tubeStop.tubeStopStatus,
          TUBE_STOP_SEQUENCE_DOCUMENT.LONGITUDE -> seq.tubeStop.longitude,
          TUBE_STOP_SEQUENCE_DOCUMENT.LATITUDE -> seq.tubeStop.latitude,
          TUBE_STOP_SEQUENCE_DOCUMENT.POLYLINE -> seq.polyLineToNextStop)
      })
      )
    })

    val newRouteDefDoc = MongoDBObject(
      TUBE_ROUTE_DEFINITION_DOCUMENT.ROUTE_ID -> tubeRoute.routeID,
      TUBE_ROUTE_DEFINITION_DOCUMENT.DIRECTION -> tubeRoute.direction,
      TUBE_ROUTE_DEFINITION_DOCUMENT.TUBE_ROUTE_BRANCHES -> branchStopSequenceList)

    val query = MongoDBObject(
      TUBE_ROUTE_DEFINITION_DOCUMENT.ROUTE_ID -> tubeRoute.routeID,
      TUBE_ROUTE_DEFINITION_DOCUMENT.DIRECTION -> tubeRoute.direction
    )

    TubeRouteDefinitionsDB.dBConnection.update(query, newRouteDefDoc, upsert = true)
    TubeRouteDefinitionsDB.incrementLogRequest(IncrementNumberInsertsCompleted(1))
  }

}
