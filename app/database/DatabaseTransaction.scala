package database

import akka.actor.ActorRef
import database.tfl.TFLMongoDBConnection
import play.api.Logger

import scala.util.{Failure, Success, Try}

trait DatabaseTransaction {

  val supervisor:ActorRef
  protected val collection:DatabaseCollections

  lazy val dBCollection =
    Try(TFLMongoDBConnection.getCollection(collection)) match {
      case Success(coll) => coll
      case Failure(fail) =>
        Logger.error("Cannot get DB Collection ")
        throw new IllegalStateException("Cannot get DB Collection "+ fail)
    }


}
