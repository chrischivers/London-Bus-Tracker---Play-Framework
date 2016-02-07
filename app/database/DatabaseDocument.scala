package database

/**
 * Database Document Objects
 */
sealed trait DatabaseDocument {
  val collection: DatabaseCollections

}

final case class POINT_TO_POINT_DOCUMENT(route_ID: String, direction_ID: Int, from_Point_ID: String, to_Point_ID: String, day_Of_Week: String, timeOffsetSeconds: Int, durationSeconds: Int, rainfall: Double) extends DatabaseDocument {
  override val collection = POINT_TO_POINT_COLLECTION
}

final case class ROUTE_DEFINITION_DOCUMENT(route_ID: String, direction_ID: Int, sequence: Int, stop_Code: String, first_Last: Option[String]) extends DatabaseDocument {
  override val collection = ROUTE_DEFINITIONS_COLLECTION
}

final case class STOP_DEFINITION_DOCUMENT(stopCode: String, stopName:String, stopType: String, towards: String, bearing:Int, indicator:String, state:Int, lat:String, lng: String) extends DatabaseDocument {
  override val collection = STOP_DEFINITIONS_COLLECTION
}


final case class POLYLINE_INDEX_DOCUMENT(fromStopCode: String, toStopCode:String, polyLine: String) extends DatabaseDocument {
  override val collection = POLYLINE_INDEX_COLLECTION
}