package database.tfl

import database.{ROUTE_DEFINITIONS_COLLECTION, STOP_DEFINITIONS_COLLECTION, DatabaseCollections, DatabaseQuery}

/**
 * Gets a StopDefinition Document
 */
object TFLGetStopDefinitionDocument extends DatabaseQuery{

  override protected val collection: DatabaseCollections = STOP_DEFINITIONS_COLLECTION

  def getDistinctStopCodes:Set[String] = {
    TFLMongoDBConnection.getCollection(ROUTE_DEFINITIONS_COLLECTION).distinct(ROUTE_DEFINITIONS_COLLECTION.POINT_ID).asInstanceOf[Seq[String]].toSet
  }


}
