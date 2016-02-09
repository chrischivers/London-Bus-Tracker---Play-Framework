package controllers

import commons.Commons._
import datadefinitions.tfl.TFLDefinitions
import org.json4s.native.JsonMethods._
import play.api.libs.json.{Json, JsObject}
import play.api.mvc.{Action, Controller}
import prediction.{KNNPredictionImpl, PredictionRequest}

object CommonFunctions extends Controller {

    def getRouteList = Action {
      val routeList = TFLDefinitions.RouteList
      val jsonMap = Map("routeList" -> Json.toJson(routeList))
      Ok(Json.toJson(jsonMap))
    }

  def getRouteListWithFirstLastStops = Action {
    val routeList = TFLDefinitions.RouteListWithFirstLaststop
    val jsonMap = Map("routeList" -> Json.toJson(routeList.map(x => x._1 + ";" + x._2 + ";" + x._3)))
    Ok(Json.toJson(jsonMap))
  }

  def getDirectionList(routeID: String) = Action {
    val outwardDirection = TFLDefinitions.PointDefinitionsMap(TFLDefinitions.RouteDefinitionMap.get(routeID, 1).get.last._2).stopPointName
    val returnDirection = TFLDefinitions.PointDefinitionsMap(TFLDefinitions.RouteDefinitionMap.get(routeID, 2).get.last._2).stopPointName
    val jsonMap = Map("directionList" -> Json.toJson(List("1," + outwardDirection, "2," + returnDirection)))
    Ok(Json.toJson(jsonMap))
  }

  def getStopList(routeID: String, directionID: Int) = Action {
    val stopList = TFLDefinitions.RouteDefinitionMap(routeID, directionID).map(x => x._2 + "," + TFLDefinitions.PointDefinitionsMap(x._2).stopPointName)
    val jsonMap = Map("stopList" -> Json.toJson(stopList))
    Ok(Json.toJson(jsonMap))
  }

  def makePrediction(routeID: String, directionID: Int, fromStop: String, toStop: String) = Action {
    val pr = new PredictionRequest(routeID, directionID, fromStop, toStop, System.currentTimeMillis().getDayCode, System.currentTimeMillis().getTimeOffset)
    val prediction = KNNPredictionImpl.makePrediction(pr)
    Ok(if (prediction.isDefined) prediction.get._1.toString + "," + prediction.get._2.toString else "Unable to make a prediction at this time")
  }

}
