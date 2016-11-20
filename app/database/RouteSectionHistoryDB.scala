package database

import akka.actor.{Actor, ActorRef, Props}
import akka.routing.RoundRobinPool
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.{Imports, MongoDBObject}
import database.RouteSectionHistoryDB.ROUTE_SECTION_HISTORY_DOCUMENT.DURATION_LIST_DOCUMENT
import database.RouteSectionHistoryDB.{ROUTE_SECTION_HISTORY_DOCUMENT, IncrementNumberPrunesCompleted, IncrementNumberPrunesRequested}
import datadefinitions.BusDefinitions._
import org.bson.types.ObjectId
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play.current

case class InsertRouteSectionRecord(busRoute: BusRoute, fromStopID: String, toStopID: String, day_Of_Week: String, timeOffsetSeconds: Int, durationSeconds: Int, rainfall: Double)
case class DeleteRouteSectionRecord(id: ObjectId)


object RouteSectionHistoryDB extends DatabaseCollections {

  @volatile var numberPruneRecordsRequested: Long = 0
  @volatile var numberPruneRecordsCompleted: Long = 0

  case class IncrementNumberPrunesRequested(incrementBy: Int) extends IncrementLogValues
  case class IncrementNumberPrunesCompleted(incrementBy: Int) extends IncrementLogValues


  case object ROUTE_SECTION_HISTORY_DOCUMENT {

    val ROUTE_ID = "ROUTE_ID"
    val DIRECTION = "DIRECTION"
    val FROM_STOP_ID = "FROM_STOP_ID"
    val TO_STOP_ID = "TO_STOP_ID"
    val DAY = "DAY"
    val DURATION_LIST = "DURATION_LIST"

    case object DURATION_LIST_DOCUMENT {
      val TIME_OFFSET = "TIME_OFFSET"
      val DURATION = "DURATION"
      val TIME_STAMP = "TIME_STAMP"
      val RAINFALL = "RAINFALL"
    }
  }

  override val supervisor: ActorRef = Akka.system.actorOf(Props[RouteSectionHistoryDBSupervisor], name = "RouteSectionHistoryDBSupervisor")

  def insertRouteSectionHistoryIntoDB(doc:InsertRouteSectionRecord) = {
    incrementLogRequest(IncrementNumberInsertsRequested(1))
    supervisor ! doc
  }

  def getRouteHistoryFromDB(route: BusRoute, fromStopID:String, toStopID:String) = {
    incrementLogRequest(IncrementNumberGetRequests(1))
    val queryObject = MongoDBObject(
      ROUTE_SECTION_HISTORY_DOCUMENT.ROUTE_ID -> route.routeID,
      ROUTE_SECTION_HISTORY_DOCUMENT.DIRECTION -> route.direction,
      ROUTE_SECTION_HISTORY_DOCUMENT.FROM_STOP_ID-> fromStopID,
      ROUTE_SECTION_HISTORY_DOCUMENT.TO_STOP_ID-> toStopID)
    dBConnection.find(queryObject)
  }


  override val collectionName: String = "RouteSectionHistory"
  override val fieldsVector = Vector(
    ROUTE_SECTION_HISTORY_DOCUMENT.ROUTE_ID,
    ROUTE_SECTION_HISTORY_DOCUMENT.DIRECTION,
    ROUTE_SECTION_HISTORY_DOCUMENT.FROM_STOP_ID,
    ROUTE_SECTION_HISTORY_DOCUMENT.TO_STOP_ID,
    ROUTE_SECTION_HISTORY_DOCUMENT.DAY)
  override val indexKeyList = List(
    (ROUTE_SECTION_HISTORY_DOCUMENT.ROUTE_ID, 1),
    (ROUTE_SECTION_HISTORY_DOCUMENT.DIRECTION, 1),
    (ROUTE_SECTION_HISTORY_DOCUMENT.FROM_STOP_ID, 1),
    (ROUTE_SECTION_HISTORY_DOCUMENT.TO_STOP_ID, 1),
    (ROUTE_SECTION_HISTORY_DOCUMENT.DAY, 1))
  override val uniqueIndex = false
  override val dBConnection: MongoCollection = MongoDatabase.getCollection(this)

}

class RouteSectionHistoryDBSupervisor extends Actor {

  val requestRouter = context.actorOf(RoundRobinPool(2).props(Props[RouteSectionHistoryDBWorker]), "RouteSectionHistoryDBRouter")

  val routeSectionHistoryDBWorker: ActorRef = context.actorOf(Props[RouteSectionHistoryDBWorker], name = "RouteSectionHistoryDBWorker")

  override def receive: Actor.Receive = {
    case doc: InsertRouteSectionRecord => requestRouter ! doc
    case doc: DeleteRouteSectionRecord => requestRouter ! doc
    case doc:IncrementNumberInsertsRequested=> RouteSectionHistoryDB.numberInsertsRequested += doc.incrementBy
    case doc:IncrementNumberInsertsCompleted => RouteSectionHistoryDB.numberInsertsCompleted += doc.incrementBy
    case doc: IncrementNumberPrunesRequested=> RouteSectionHistoryDB.numberPruneRecordsRequested += doc.incrementBy
    case doc:IncrementNumberPrunesCompleted => RouteSectionHistoryDB.numberPruneRecordsCompleted += doc.incrementBy
    case doc:IncrementNumberGetRequests => RouteSectionHistoryDB.numberGetRequests += doc.incrementBy
    case doc: IncrementNumberDeleteRequests => RouteSectionHistoryDB.numberDeleteRequests += doc.incrementBy
    case _ =>
      Logger.error("RouteSectionHistoryDBSupervisor Actor received unknown message")
      throw new IllegalStateException("RouteSectionHistoryDBSupervisor received unknown message")
  }
}

class RouteSectionHistoryDBWorker extends Actor {


  override def receive: Receive = {
    case doc: InsertRouteSectionRecord => insertToDB(doc)
    case doc: DeleteRouteSectionRecord => deleteFromDB(doc)
    case _ =>
      Logger.error("RouteSectionHistoryDBWorker Actor received unknown message")
      throw new IllegalStateException("RouteSectionHistoryDBWorker received unknown message")
  }


  private def insertToDB(doc: InsertRouteSectionRecord) = {

    val routeObject = MongoDBObject(
      ROUTE_SECTION_HISTORY_DOCUMENT.ROUTE_ID -> doc.busRoute.routeID,
      ROUTE_SECTION_HISTORY_DOCUMENT.DIRECTION -> doc.busRoute.direction,
      ROUTE_SECTION_HISTORY_DOCUMENT.FROM_STOP_ID -> doc.fromStopID,
      ROUTE_SECTION_HISTORY_DOCUMENT.TO_STOP_ID -> doc.toStopID,
      ROUTE_SECTION_HISTORY_DOCUMENT.DAY -> doc.day_Of_Week)

    val durationListObject = MongoDBObject(
      DURATION_LIST_DOCUMENT.DURATION -> doc.durationSeconds,
      DURATION_LIST_DOCUMENT.TIME_OFFSET -> doc.timeOffsetSeconds,
      DURATION_LIST_DOCUMENT.RAINFALL -> doc.rainfall,
      DURATION_LIST_DOCUMENT.TIME_STAMP -> System.currentTimeMillis())

    val pushUpdate = $push(ROUTE_SECTION_HISTORY_DOCUMENT.DURATION_LIST -> durationListObject)
    val update = RouteSectionHistoryDB.dBConnection.update(routeObject, pushUpdate, upsert = true)
    RouteSectionHistoryDB.incrementLogRequest(IncrementNumberInsertsCompleted(1))
    if (update.isUpdateOfExisting) pruneDBArray()


    def pruneDBArray() = {

      val PRUNE_THRESHOLD_K_LIMIT = 10
      val PRUNE_THRESHOLD_TIME_LIMIT = 3600
      val PRUNE_THRESHOLD_RAINFALL_LIMIT = 1

      val cursor = RouteSectionHistoryDB.dBConnection.find(routeObject)
      assert(cursor.length == 1)
      if (cursor.length == 1) {

        val record = cursor.next().asInstanceOf[Imports.BasicDBObject]
        val durListVector = getDurListVectorFromCursor(record)

        // This filters those within the PRUNE THRESHOLD LIMIT followed by those within the rainfall threshold
        val prunedVector = durListVector.filter(x =>
          math.abs(x._2 - doc.timeOffsetSeconds) <= PRUNE_THRESHOLD_TIME_LIMIT &&
            math.abs(x._4 - doc.rainfall) <= PRUNE_THRESHOLD_RAINFALL_LIMIT)
        val excessRecords = prunedVector.size - PRUNE_THRESHOLD_K_LIMIT


        if (excessRecords > 0) {
          RouteSectionHistoryDB.incrementLogRequest(IncrementNumberPrunesRequested(excessRecords))
          // Delete all records above the K Threshold
          val recordsToDelete = prunedVector.sortBy(_._3).take(excessRecords)
          recordsToDelete.foreach(x => {
            val updatePull = $pull(ROUTE_SECTION_HISTORY_DOCUMENT.DURATION_LIST -> MongoDBObject(
              DURATION_LIST_DOCUMENT.DURATION -> x._1,
              DURATION_LIST_DOCUMENT.TIME_OFFSET -> x._2,
              DURATION_LIST_DOCUMENT.TIME_STAMP -> x._3,
              DURATION_LIST_DOCUMENT.RAINFALL -> x._4))
            RouteSectionHistoryDB.dBConnection.update(routeObject, updatePull)
            RouteSectionHistoryDB.incrementLogRequest(IncrementNumberPrunesCompleted(1))
          })
        }

      }
    }

    /*
   *
   * @param dbObject The database document
   * @return A vector of Duration, Time Offset, Time Stamp and Time Offset Difference
   */
    def getDurListVectorFromCursor(dbObject: Imports.MongoDBObject): Vector[(Int, Int, Long, Double)] =
    {
      dbObject.get(ROUTE_SECTION_HISTORY_DOCUMENT.DURATION_LIST).get.asInstanceOf[Imports.BasicDBList].map(y => {
        (y.asInstanceOf[Imports.BasicDBObject].getInt(DURATION_LIST_DOCUMENT.DURATION),
          y.asInstanceOf[Imports.BasicDBObject].getInt(DURATION_LIST_DOCUMENT.TIME_OFFSET),
          y.asInstanceOf[Imports.BasicDBObject].getLong(DURATION_LIST_DOCUMENT.TIME_STAMP),
          y.asInstanceOf[Imports.BasicDBObject].getDouble(DURATION_LIST_DOCUMENT.RAINFALL))
      })
        .toVector
    }
  }

    private def deleteFromDB(deleteRequest: DeleteRouteSectionRecord) = {

      val query = MongoDBObject("_id" -> deleteRequest.id)

      val writeConcern = RouteSectionHistoryDB.dBConnection.remove(query)
      assert(writeConcern.getN == 1)

      RouteSectionHistoryDB.incrementLogRequest(IncrementNumberDeleteRequests(1))
    }
}