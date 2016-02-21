package streaming

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import commons.Commons
import org.atmosphere.config.service
import org.atmosphere.config.service.{Disconnect, ManagedService, Ready}
import org.atmosphere.cpr._
import play.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka


final case class Push(routeID: String, latitude: Double, longitude: Double, msg: String)
final case class PushToChildren(pso: PackagedStreamObject)
final case class Subscribe(uuid: String, actor:ActorRef)
final case class Unsubscribe(uuid: String)

@ManagedService(path="/atmosphere")
class Atmosphere {

  @Ready
  def onReady(r: AtmosphereResource) = {
    Logger.info("socket connected : {}", r.uuid())
    AtmosphereSupervisor.props(r)
  }

  @Disconnect
  def onDisconnect(e: AtmosphereResourceEvent): Unit = {
    AtmosphereSupervisor.supervisor ! Unsubscribe(e.getResource.uuid())
  }

  @service.Message
  def onMessage(r: AtmosphereResource, s: String) = {
    val actor = AtmosphereSupervisor.connectedActors.get(r.uuid())
    if (actor.isDefined) actor.get ! s
  }
}


object AtmosphereSupervisor {

  @volatile var connectedActors:Map[String, ActorRef] = Map()
  val supervisor = Akka.system.actorOf(Props[AtmosphereSupervisor])

  def props(r: AtmosphereResource) = Akka.system.actorOf(Props(classOf[AtmosphereActor],r))

  def PushToChildren(pso: PackagedStreamObject) = {
    val encoded = Commons.encodePackageObject(pso)
    val firstLat = if (!pso.markerMovementData.isEmpty) pso.markerMovementData(0)._1.toDouble else 0
    val firstLng = if (!pso.markerMovementData.isEmpty) pso.markerMovementData(0)._2.toDouble else 0
    connectedActors.foreach(ref => ref._2 ! Push(pso.route_ID, firstLat, firstLng, encoded))
  }
}

class AtmosphereSupervisor extends Actor {
  
  override def receive: Receive = {
    case Subscribe(id, ref) => AtmosphereSupervisor.connectedActors += (id -> ref)
    case Unsubscribe(id) =>
      val actor = AtmosphereSupervisor.connectedActors.get(id)
      if (actor.isDefined) actor.get ! PoisonPill
      AtmosphereSupervisor.connectedActors -= id
  }
}

class AtmosphereActor(r: AtmosphereResource) extends Actor {
  Logger.info("new atmosphere actor created")
  var mode = "NONE"
  var selectedRadius = 0.0
  var routeList: List[String] = List()
  var centrePoint: Array[Double] = Array()

  override def preStart(): Unit = {
    AtmosphereSupervisor.supervisor ! Subscribe(r.uuid(), self)
  }

  def receive = {
    case msg: String =>
      if (msg.startsWith("ROUTELIST")) {
        mode = "ROUTELIST"
        val splitReceive = msg.split(",").drop(1).toList
        routeList = routeList ++ splitReceive
        //logger.info("1 connection made. Route List: " + routeList)
      } else if (msg.startsWith("RADIUS")) {
        mode = "RADIUS"
        // println("Received String: " + msg)
        val temporaryStr = msg.replaceAll("\\)", "").replaceAll("\\(", "").split(",").drop(1).map(_.toDouble) //Take out brackets
        selectedRadius = temporaryStr.head //Sets the radius
        // logger.info("1 connection made. Radius " + selectedRadius)
        centrePoint = temporaryStr.drop(1) // Sets the centre Point
      }
    case Push(routeID: String, latitude: Double, longitude: Double, message: String) =>
      if (AtmosphereSupervisor.connectedActors.get(r.uuid()).isDefined) {
        if (mode == "ROUTELIST" && routeList.contains(routeID)) r.write(message)
        else if (mode == "RADIUS") {
          val centreLat = centrePoint(0)
          val centreLng = centrePoint(1)
          if (Commons.getDistance(centreLat, centreLng, latitude, longitude) < selectedRadius) r.write(message)
        }
      }
  }

  override def postStop() = {
    AtmosphereSupervisor.supervisor ! Unsubscribe(r.uuid)
  }

}