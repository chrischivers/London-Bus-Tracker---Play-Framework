package datasource

import org.apache.http.auth.AuthScope
import play.Play


object TFLDataSourceImpl extends DataSource {

  override val URL = Play.application.configuration.getString("tfldatasourceurl")
  //override val URL = "http://countdown.api.tfl.gov.uk:80/interfaces/ura/stream_V1?LineName=3&ReturnList=StopCode1,LineName,DirectionID,RegistrationNumber,EstimatedTime"
  override val USERNAME = Play.application.configuration.getString("tfldatasourceusername")
  override val PASSWORD = Play.application.configuration.getString("tfldatasourcepassword")
  override  val CONNECTION_TIMEOUT:Int = 3000
  override val AUTHSCOPE = new AuthScope("countdown.api.tfl.gov.uk", 80)
  override val NUMBER_LINES_TO_DISREGARD = 1

}
