package database.tfl

import akka.actor.{Actor, ActorRef, Props}
import com.mongodb.casbah.commons.MongoDBObject
import database.{POINT_TO_POINT_COLLECTION, DatabaseCollections, DatabaseDelete}
import org.bson.types.ObjectId
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play.current

/**
 * Deletes a PointToPointDuration asyncronously
 */
object TFLDeletePointToPointDuration extends DatabaseDelete {



  protected val collection: DatabaseCollections = POINT_TO_POINT_COLLECTION

  override val supervisor: ActorRef = Akka.system.actorOf(Props[TFLDeletePointToPointDuration], "TFLDeletePointToPointDurationActor")
}

class TFLDeletePointToPointDurationSupervisor extends Actor {

  val dbTransactionActor: ActorRef = context.actorOf(Props[TFLDeletePointToPointDuration], name = "TFLDeletePointToPointDurationActor")

  override def receive: Actor.Receive = {
    case docID: ObjectId => dbTransactionActor ! docID
  }

}

class TFLDeletePointToPointDuration extends Actor{

  override def receive: Receive = {
    case docID: ObjectId => deleteFromDB(docID)
    case _ =>
      Logger.error("TFL Delete Point Actor received unknown message")
      throw new IllegalStateException("TFL Delete Point Actor received unknown message")
  }


  private def deleteFromDB(docID: ObjectId) = {

   val query = MongoDBObject(
     "_id" -> docID)

    val writeConcern = TFLDeletePointToPointDuration.dBCollection.remove(query)
    assert(writeConcern.getN == 1)
  }
}

