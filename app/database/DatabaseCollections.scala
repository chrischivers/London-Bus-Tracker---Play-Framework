package database

/**
 * Database Collection Objects
 */
sealed trait DatabaseCollections {
  val name: String
  val fieldsVector: Vector[String]
  val indexKeyList: List[(String,Int)]
  val uniqueIndex: Boolean
}

/**
 * The POINT_TO_POINT Collection as stored by MongoDB
 */
case object POINT_TO_POINT_COLLECTION extends DatabaseCollections {


  val ROUTE_ID = "ROUTE_ID"
  val DIRECTION_ID = "DIRECTION_ID"
  val FROM_POINT_ID = "FROM_POINT_ID"
  val TO_POINT_ID = "TO_POINT_ID"
  val DAY = "DAY"
  val DURATION_LIST = "DURATION_LIST"
  val TIME_OFFSET = "TIME_OFFSET"
  val DURATION = "DURATION"
  val TIME_STAMP = "TIME_STAMP"
  val RAINFALL = "RAINFALL"

  override val name: String = "PointToPoint"
  override val fieldsVector = Vector(ROUTE_ID, DIRECTION_ID, FROM_POINT_ID, TO_POINT_ID, DAY, TIME_OFFSET, DURATION, TIME_STAMP, RAINFALL)
  override val indexKeyList = List((ROUTE_ID, 1),(DIRECTION_ID, 1),(FROM_POINT_ID, 1),(TO_POINT_ID, 1), (DAY, 1))
  override val uniqueIndex = false
}

/**
 * The ROUTE_DEFINITIONS Collection as stored by MongoDB
 */
case object ROUTE_DEFINITIONS_COLLECTION extends DatabaseCollections {

  val ROUTE_ID = "ROUTE_ID"
  val DIRECTION_ID = "DIRECTION_ID"
  val SEQUENCE = "SEQUENCE"
  val POINT_ID = "POINT_ID"
  val FIRST_LAST = "FIRST_LAST"
  val POLYLINE = "POLYLINE"

  override val name: String = "RouteDefinitions"
  override val fieldsVector = Vector(ROUTE_ID, DIRECTION_ID, SEQUENCE, POINT_ID, FIRST_LAST, POLYLINE)
  override val indexKeyList = List((ROUTE_ID, 1),(DIRECTION_ID, 1),(SEQUENCE, 1))
  override val uniqueIndex = true

}

/**
 * The STOP_DEFINITIONS Collection as stored by MongoDB
 */
case object STOP_DEFINITIONS_COLLECTION extends DatabaseCollections {

  val STOP_CODE = "STOP_CODE"
  val STOP_NAME = "STOP_NAME"
  val STOP_TYPE = "STOP_TYPE"
  val TOWARDS = "TOWARDS"
  val BEARING = "BEARING"
  val INDICATOR = "INDICATOR"
  val STATE = "STATE"
  val LAT = "LAT"
  val LNG = "LNG"

  override val name: String = "StopDefinitions"
  override val fieldsVector = Vector(STOP_CODE, STOP_NAME, STOP_TYPE, TOWARDS, BEARING, INDICATOR, STATE, LAT, LNG)
  override val indexKeyList = List((STOP_CODE, 1))
  override val uniqueIndex = true

}

/**
* The POLYLINE INDEX Collection as stored by MongoDB
*/
case object POLYLINE_INDEX_COLLECTION extends DatabaseCollections {

  val FROM_STOP_CODE = "FROM_STOP_CODE"
  val TO_STOP_CODE = "TO_STOP_CODE"
  val POLYLINE = "POLYLINE"

  override val name: String = "PolyLineDefinitions"
  override val fieldsVector = Vector(FROM_STOP_CODE, TO_STOP_CODE, POLYLINE)
  override val indexKeyList = List((FROM_STOP_CODE, 1),(TO_STOP_CODE, 1))
  override val uniqueIndex = true

}
