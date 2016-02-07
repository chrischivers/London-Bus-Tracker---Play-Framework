package datasource

import org.apache.http.auth.AuthScope

trait DataSource {

  val URL: String
  val USERNAME: String
  val PASSWORD: String
  val CONNECTION_TIMEOUT:Int
  val AUTHSCOPE: AuthScope
  val NUMBER_LINES_TO_DISREGARD: Int

  val ROUTE_ID = "ROUTE_ID"
  val DIRECTION_ID = "DIRECTION_ID"
  val OBJECT_ID = "OBJECT_ID"
  val POINT_ID = "POINT_ID"
  val ARRIVAL_TIMESTAMP = "ARRIVAL_TIMESTAMP"

  val fieldVector: Vector[String] = Vector(ROUTE_ID, DIRECTION_ID, OBJECT_ID, POINT_ID,ARRIVAL_TIMESTAMP)
}
