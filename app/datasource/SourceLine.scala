package datasource

import datadefinitions.BusDefinitions.{BusRoute}

case class SourceLine(busRoute: BusRoute, busStopID: String, busRegistration: String, arrival_TimeStamp: Long, destination:String) {

  def geFieldValueMap(): Map[String, Any] = {
    TFLDataSourceImpl.fieldVector
      .zip(Vector(busRoute, busStopID, busRegistration, arrival_TimeStamp,destination)) //zips array fields with their key values
      .toMap
  }

  override def toString: String = "Line(" + busRoute + "," + busStopID + "," + busRegistration + "," + arrival_TimeStamp + "," + destination +")"
}
