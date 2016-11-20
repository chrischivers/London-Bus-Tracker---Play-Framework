package prediction

import datadefinitions.BusDefinitions.BusRoute

/**
 * A Prediction Request Object encapsulating the fields required to make a prediction
 * @param busRoute The BusRouteObject
 * @param fromStopID The From Stop ID
 * @param toStopID The To Stop ID
 * @param day_Of_Week The Day Of The Week
 * @param timeOffset The Time Offset
 */
case class PredictionRequest(busRoute:BusRoute, fromStopID: String, toStopID: String, day_Of_Week: String, timeOffset: Int)
