package datasource.tfl

import datasource.SourceLine

case class TFLSourceLineImpl(stop_Code: String, route_ID: String, direction_ID: Int, vehicle_Reg: String, arrival_TimeStamp: Long, destination:String) extends SourceLine {

  override def geFieldValueMap(): Map[String, Any] = {
    TFLDataSourceImpl.fieldVector
      .zip(Vector(route_ID, direction_ID, vehicle_Reg, stop_Code, arrival_TimeStamp,destination)) //zips array fields with their key values
      .toMap
  }

  override def toString: String = "Line(" + stop_Code + "," + route_ID + "," + direction_ID + "," + vehicle_Reg + "," + arrival_TimeStamp + "," + destination +")"
}
