package datasource.tfl

import commons.ReadProperties
import datasource.DataSource
import org.apache.http.auth.AuthScope


object TFLDataSourceImpl extends DataSource {

  override val URL = ReadProperties.getProperty("tfldatasourceurl")
  //override val URL = "http://countdown.api.tfl.gov.uk:80/interfaces/ura/stream_V1?LineName=3&ReturnList=StopCode1,LineName,DirectionID,RegistrationNumber,EstimatedTime"
  override val USERNAME = ReadProperties.getProperty("tfldatasourceusername")
  override val PASSWORD = ReadProperties.getProperty("tfldatasourcepassword")
  override  val CONNECTION_TIMEOUT:Int = 3000
  override val AUTHSCOPE = new AuthScope("countdown.api.tfl.gov.uk", 80)
  override val NUMBER_LINES_TO_DISREGARD = 1

}
