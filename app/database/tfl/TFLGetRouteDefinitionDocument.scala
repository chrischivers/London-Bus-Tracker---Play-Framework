package database.tfl

import com.mongodb.casbah.MongoCursor
import com.mongodb.casbah.commons.MongoDBObject
import database.{ROUTE_DEFINITIONS_COLLECTION, DatabaseCollections, DatabaseQuery}


/**
 * Gets a TFL Route Definition Document
 */
object TFLGetRouteDefinitionDocument extends DatabaseQuery{

  override protected val collection: DatabaseCollections = ROUTE_DEFINITIONS_COLLECTION

  def fetchAllOrdered():MongoCursor= {
    dBCollection.find().sort(MongoDBObject(
      ROUTE_DEFINITIONS_COLLECTION.ROUTE_ID -> 1,
      ROUTE_DEFINITIONS_COLLECTION.DIRECTION_ID -> 1,
      ROUTE_DEFINITIONS_COLLECTION.SEQUENCE -> 1))
  }


}
