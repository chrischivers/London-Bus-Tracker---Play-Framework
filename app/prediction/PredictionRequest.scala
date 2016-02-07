package prediction

/**
 * A Prediction Request Object encapsulating the fields required to make a prediction
 * @param route_ID The Route ID
 * @param direction_ID The Direction ID
 * @param from_Point_ID The From Point ID
 * @param to_Point_ID The To Point ID
 * @param day_Of_Week The Day Of The Week
 * @param timeOffset The Time Offset
 */
case class PredictionRequest(route_ID: String, direction_ID: Int, from_Point_ID: String, to_Point_ID: String, day_Of_Week: String, timeOffset: Int)
