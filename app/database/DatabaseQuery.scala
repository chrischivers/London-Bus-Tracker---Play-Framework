package database

import com.mongodb.casbah.MongoCursor
import com.mongodb.casbah.commons.Imports
import database.tfl.TFLMongoDBConnection

import scala.util.{Failure, Success, Try}


/**
 * Interface for queries in the database
 */
trait DatabaseQuery {

  @volatile var numberDBQueriesRun:Long = 0
  protected val collection:DatabaseCollections

  lazy val dBCollection =
    Try(TFLMongoDBConnection.getCollection(collection)) match {
      case Success(coll) => coll
      case Failure(fail) => throw new IllegalStateException("Cannot get DB Collection "+ fail)
    }


  def executeQuery(mongoObj: Imports.DBObject): MongoCursor = {
    dBCollection.find(mongoObj)
  }

  def fetchAll():MongoCursor= {
    dBCollection.find()
  }
}
