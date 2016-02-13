package database.tfl

import akka.actor.{Actor, Props}
import akka.routing.RoundRobinPool
import com.mongodb.casbah.MongoCursor
import com.mongodb.casbah.commons.{MongoDBObject, Imports}
import database.{POINT_TO_POINT_DOCUMENT, DatabaseDocument, POINT_TO_POINT_COLLECTION, DatabaseInsert}
import com.mongodb.casbah.Imports._
import play.api.libs.concurrent.Akka
import play.api.Play.current


case class PruneRequest(mongoObj: Imports.DBObject, timeOffSet: Int, rainfall: Double)
case class PruneNumberDeletesRequested(n:Int)
case class InsertCompleted()
case class PruneCompleted()

object TFLInsertPointToPointDurationSupervisor extends DatabaseInsert {
  val collection = POINT_TO_POINT_COLLECTION
  override val supervisor = Akka.system.actorOf(Props[TFLInsertPointToPointDurationSupervisor], "InsertPointToPointSupervisor")

  @volatile var numberRecordsPulledFromDbRequested: Long = 0
  @volatile var numberRecordsPulledFromDbExecuted: Long = 0

}

class  TFLInsertPointToPointDurationSupervisor extends Actor {


  val insertRouter = context.actorOf(RoundRobinPool(2).props(Props[TFLInsertPointToPointDurationActor]), "InsertPointToPointRouter")
  val pruneRouter = context.actorOf(RoundRobinPool(2).props(Props[TFLPrunePointToPointActor]), "PrunePointToPointRouter")


  override def receive = {
    case doc: DatabaseDocument => insertDoc(doc)
    case InsertCompleted =>   TFLInsertPointToPointDurationSupervisor.numberDBTransactionsExecuted += 1
    case po: PruneRequest => pruneDatabaseArray(po)
    case PruneCompleted =>TFLInsertPointToPointDurationSupervisor.numberRecordsPulledFromDbExecuted += 1
    case pndr: PruneNumberDeletesRequested =>  TFLInsertPointToPointDurationSupervisor.numberRecordsPulledFromDbRequested += pndr.n
  }


  def insertDoc(doc: DatabaseDocument): Unit = {
    if (TFLInsertPointToPointDurationSupervisor.numberDBTransactionsRequested - TFLInsertPointToPointDurationSupervisor.numberDBTransactionsExecuted < TFLInsertPointToPointDurationSupervisor.MAXIMUM_OUTSTANDING_TRANSACTIONS) {
      insertRouter ! doc
      TFLInsertPointToPointDurationSupervisor.numberDBTransactionsRequested += 1
    }
    else TFLInsertPointToPointDurationSupervisor.numberDBTransactionsDroppedDueToOverflow += 1
  }

  def pruneDatabaseArray(pruneObj: PruneRequest) = {
    pruneRouter ! pruneObj
  }

}

class TFLInsertPointToPointDurationActor extends Actor {

  val collection = TFLInsertPointToPointDurationSupervisor.collection
  val dbCollection = TFLInsertPointToPointDurationSupervisor.dBCollection

  override def receive: Receive = {
    case doc1: POINT_TO_POINT_DOCUMENT => insertToDB(doc1)
    case e => throw new IllegalStateException("TFL Insert Point Actor received unknown message. Message: " + e)
  }


  private def insertToDB(doc: POINT_TO_POINT_DOCUMENT) = {

    val newObj = MongoDBObject(
      collection.ROUTE_ID -> doc.route_ID,
      collection.DIRECTION_ID -> doc.direction_ID,
      collection.FROM_POINT_ID -> doc.from_Point_ID,
      collection.TO_POINT_ID -> doc.to_Point_ID,
      collection.DAY -> doc.day_Of_Week)

    val pushUpdate = $push(collection.DURATION_LIST -> MongoDBObject(collection.DURATION -> doc.durationSeconds, collection.TIME_OFFSET -> doc.timeOffsetSeconds, collection.RAINFALL -> doc.rainfall, collection.TIME_STAMP -> System.currentTimeMillis()))
    val update = dbCollection.update(newObj, pushUpdate, upsert = true)
    TFLInsertPointToPointDurationSupervisor.supervisor ! InsertCompleted
    if (update.isUpdateOfExisting) {
      TFLInsertPointToPointDurationSupervisor.supervisor ! new PruneRequest(newObj, doc.timeOffsetSeconds, doc.rainfall)
    }
  }
}
class TFLPrunePointToPointActor extends Actor {

  val collection = POINT_TO_POINT_COLLECTION
  
  val PRUNE_THRESHOLD_K_LIMIT = 10
  val PRUNE_THRESHOLD_TIME_LIMIT = 3600
  val PRUNE_THRESHOLD_RAINFALL_LIMIT = 1

  override def receive: Actor.Receive = {
    case pruneObj: PruneRequest => pruneDBArray(pruneObj) 
    case _ => throw new IllegalStateException("TFL PruneActor received unknown message")
  }

  private def pruneDBArray(pruneObj: PruneRequest) = {

    val cursor: MongoCursor = TFLGetPointToPointDocument.executeQuery(pruneObj.mongoObj)
    if (cursor.length == 1) {

      val record = cursor.next().asInstanceOf[Imports.BasicDBObject]

      val durListVector = getDurListVectorFromCursor(record)

      // This filters those within the PRUNE THRESHOLD LIMIT followed by those within the rainfall threshold
      val prunedVector = durListVector.filter(x =>
        math.abs(x._2 - pruneObj.timeOffSet) <= PRUNE_THRESHOLD_TIME_LIMIT &&
          math.abs(x._4 - pruneObj.rainfall) <= PRUNE_THRESHOLD_RAINFALL_LIMIT)
      val excessRecords = prunedVector.size - PRUNE_THRESHOLD_K_LIMIT


      if (excessRecords > 0) {
        TFLInsertPointToPointDurationSupervisor.supervisor ! PruneNumberDeletesRequested(excessRecords)
        // Delete all records above the K Threshold
        val recordsToDelete = prunedVector.sortBy(_._3).take(excessRecords)
        recordsToDelete.foreach(x=> {
          val updatePull = $pull(collection.DURATION_LIST -> MongoDBObject(collection.DURATION -> x._1, collection.TIME_OFFSET -> x._2, collection.TIME_STAMP -> x._3, collection.RAINFALL -> x._4))
          TFLInsertPointToPointDurationSupervisor.dBCollection.update(pruneObj.mongoObj, updatePull)
          TFLInsertPointToPointDurationSupervisor.supervisor ! PruneCompleted
        })
      }

    }
  }

  /*
   *
   * @param dbObject The database document
   * @return A vector of Duration, Time Offset, Time Stamp and Time Offset Difference
   */
  private def getDurListVectorFromCursor(dbObject: Imports.MongoDBObject): Vector[(Int, Int, Long, Double)] = {
    dbObject.get(collection.DURATION_LIST).get.asInstanceOf[Imports.BasicDBList].map(y => {
      (y.asInstanceOf[Imports.BasicDBObject].getInt(collection.DURATION),
        y.asInstanceOf[Imports.BasicDBObject].getInt(collection.TIME_OFFSET),
        y.asInstanceOf[Imports.BasicDBObject].getLong(collection.TIME_STAMP),
        y.asInstanceOf[Imports.BasicDBObject].getDouble(collection.RAINFALL))
    })
      .toVector
  }
}