package controllers

import commons.Commons._
import datadefinitions.BusDefinitions
import datadefinitions.BusDefinitions.BusRoute
import play.api.libs.json.{Json}
import play.api.mvc.{Action, Controller}
import prediction.{KNNPredictionImpl, PredictionRequest}

object CommonFunctions extends Controller {

    def getRouteList = Action {
      val routeList = BusDefinitions.sortedBusRouteList
      if (!routeList.isEmpty) {
        val jsonMap = Map("routeList" -> Json.toJson(routeList))
        Ok(Json.toJson(jsonMap))
      } else {
        Ok("No routes available")
      }
    }

  def getRouteListWithFirstLastStops = Action {
    val routeList = BusDefinitions.sortedBusRouteListWithFirstLastStops
    val jsonMap = Map("routeList" -> Json.toJson(routeList.map(x => x._1 + ";" + x._2 + ";" + x._3)))
    Ok(Json.toJson(jsonMap))
  }

  def getDirectionList(routeID: String) = Action {
    val outwardDirection = BusDefinitions.busRouteDefinitions.filter(key => key._1.routeID == routeID && key._1.direction == "outbound").head._2.last.busStop.busStopName
    val returnDirection = BusDefinitions.busRouteDefinitions.filter(key => key._1.routeID == routeID && key._1.direction == "inbound").head._2.last.busStop.busStopName
    val jsonMap = Map("directionList" -> Json.toJson(List("outbound," + outwardDirection, "inbound," + returnDirection)))
    Ok(Json.toJson(jsonMap))
  }

  def getStopList(routeID: String, direction: String) = Action {
    val stopList = BusDefinitions.busRouteDefinitions(BusRoute(routeID, direction)).map(x => x.busStop.busStopID + "," + x.busStop.busStopName)
    val jsonMap = Map("stopList" -> Json.toJson(stopList))
    Ok(Json.toJson(jsonMap))
  }

  def makePrediction(routeID: String, direction: String, fromStopID: String, toStopID: String) = Action {
    val busRoute = BusRoute(routeID, direction)
    val pr = new PredictionRequest(busRoute, fromStopID, toStopID, System.currentTimeMillis().getDayCode, System.currentTimeMillis().getTimeOffset)
    val prediction = KNNPredictionImpl.makePrediction(pr)
    Ok(if (prediction.isDefined) prediction.get._1.toString + "," + prediction.get._2.toString else "Unable to make a prediction at this time")
  }

}
