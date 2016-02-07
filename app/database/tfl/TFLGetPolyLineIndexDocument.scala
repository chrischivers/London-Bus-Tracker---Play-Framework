package database.tfl

import database.{DatabaseCollections, POLYLINE_INDEX_COLLECTION, DatabaseQuery}

/**
 * Gets a PolyLineIndex Document
 */
object TFLGetPolyLineIndexDocument extends  DatabaseQuery {
  override protected val collection: DatabaseCollections = POLYLINE_INDEX_COLLECTION

}
