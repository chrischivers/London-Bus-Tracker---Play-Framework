package streaming

import akka.actor._
import commons.Commons
import play.api.mvc.WebSocket


final case class Push(routeID: String, latitude: Double, longitude: Double, msg: String)
final case class PushToChildren(pso: PackagedStreamObject)

object MyWebSocketActor {
  @volatile var connectedActors:Set[ActorRef] = Set()

  def props(out: ActorRef) = {
    connectedActors += out
    Props(new MyWebSocketActor(out))
  }

  def PushToChildren(pso: PackagedStreamObject) = {
    val encoded = Commons.encodePackageObject(pso)
    val firstLat = if (!pso.markerMovementData.isEmpty) pso.markerMovementData(0)._1.toDouble else 0
    val firstLng = if (!pso.markerMovementData.isEmpty) pso.markerMovementData(0)._2.toDouble else 0
    connectedActors.foreach(ref => ref ! Push(pso.route_ID, firstLat, firstLng, encoded))
  }
}

class MyWebSocketActor(out: ActorRef) extends Actor {

  var mode = "NONE"
  var selectedRadius = 0.0
  var routeList: List[String] = List()
  var centrePoint: Array[Double] = Array()

  def receive = {
    case msg: String =>
      if (msg.startsWith("ROUTELIST")) {
        mode = "ROUTELIST"
        val splitReceive = msg.split(",").drop(1).toList
        routeList = routeList ++ splitReceive
        //logger.info("1 connection made. Route List: " + routeList)
      } else if (msg.startsWith("RADIUS")) {
        mode = "RADIUS"
        println("Received String: " + msg);
        val temporaryStr = msg.replaceAll("\\)", "").replaceAll("\\(", "").split(",").drop(1).map(_.toDouble) //Take out brackets
        selectedRadius = temporaryStr.head //Sets the radius
        // logger.info("1 connection made. Radius " + selectedRadius)
        centrePoint = temporaryStr.drop(1) // Sets the centre Point
      }
    case Push(routeID: String, latitude: Double, longitude: Double, message: String) =>
      if (mode == "ROUTELIST") {
        // Sends a text frame to the clients that are listening to that particular route
        if (routeList.contains(routeID)) {
          out ! message
        }
      } else if (mode == "RADIUS") {
        val centreLat = centrePoint(0)
        val centreLng = centrePoint(1)

        if (Commons.getDistance(centreLat, centreLng, latitude, longitude) < selectedRadius) {
          out ! message
        }
      }
  }

  override def postStop() = {
    MyWebSocketActor.connectedActors -= out
  }

}