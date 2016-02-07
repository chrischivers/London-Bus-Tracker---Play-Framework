package database.tfl

import database.{POINT_TO_POINT_COLLECTION, DatabaseCollections, DatabaseQuery}

/**
 * Gets a PointToPointDuration
 */
object TFLGetPointToPointDocument extends  DatabaseQuery {

  override protected val collection: DatabaseCollections = POINT_TO_POINT_COLLECTION

}
