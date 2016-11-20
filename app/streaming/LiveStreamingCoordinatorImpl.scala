package streaming

import akka.actor.SupervisorStrategy.Escalate
import akka.actor._
import datadefinitions.BusDefinitions
import datadefinitions.BusDefinitions.BusRoute
import datasource.SourceLine
import play.api.Logger

import scala.concurrent.duration._

/**
 * Case class for the packaged stream object sent to clients
 * @param reg The vehicle reg
 * @param nextArrivalTime The arrival time at the next point
 * @param markerMovementData An array of marker movement data (Lat, Lng, Rotation To Here, Proportional Distance To Here)
 * @param route_ID The route ID
 * @param direction The direction
 * @param towards Towards
 * @param nextStopID The next Stop ID
 * @param nextStopName the next Stop Name
 */
case class PackagedStreamObject(reg: String, nextArrivalTime: String, markerMovementData: Array[(String, String, String, String)], route_ID: String, direction: String, towards: String, nextStopID: String, nextStopName: String)
case class LiveActorDetails(actorRef:ActorRef, busRoute:BusRoute, lastLatitude:String, lastLongitude:String, lastUpdated:Long)
case class KillMessage(vehicleID: String, busRoute: BusRoute, lastLatitude: String, lastLongitude: String)

object LiveStreamingCoordinatorImpl extends LiveStreamingCoordinator{

  //val logger = Logger[this.type]

  override val CACHE_HOLD_FOR_TIME: Int = 600000
  override val IDLE_TIME_UNTIL_ACTOR_KILLED: Int = 600000
}

/**
 * The Supervising Actor
 */
class LiveVehicleSupervisor extends Actor  {


  /**
   * The record of live actors. A Map of the VehicleID to the ActorRef, The Route, and the time last updated
   */
  @volatile var liveActors = Map[String, LiveActorDetails]()

  override def receive = {
    case liveSourceLine: SourceLine => processLine(liveSourceLine)
    case km: KillMessage => killActor(km)
    case actor:Terminated => liveActors -= actor.getActor.path.name
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception =>
        Logger.debug("Vehicle actor exception")
        e.printStackTrace()
        Escalate
      case t =>
        super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)
    }

  /**
   * Processes an incoming line. If it is for a vehicle already in progress (in Live Actors List), then send a message the respective actor
   * If not already in progress, create a new actor and send the line
   * @param liveSourceLine The incoming source line
   */
  private def processLine(liveSourceLine: SourceLine) = {
    val busStop = BusDefinitions.busRouteDefinitions(liveSourceLine.busRoute).filter(x => x.busStop.busStopID == liveSourceLine.busStopID).head.busStop
    if (liveActors.contains(liveSourceLine.busRegistration)) {
      val currentVehicleActor = liveActors(liveSourceLine.busRegistration).actorRef
      // Update timestamp
      liveActors += (liveSourceLine.busRegistration -> LiveActorDetails(currentVehicleActor, liveSourceLine.busRoute, busStop.latitude, busStop.longitude, System.currentTimeMillis()))
      currentVehicleActor ! liveSourceLine
    } else {
      val newVehicleActor = context.actorOf(Props[VehicleActor], liveSourceLine.busRegistration)
      context.watch(newVehicleActor)
      liveActors += (liveSourceLine.busRegistration -> LiveActorDetails(newVehicleActor, liveSourceLine.busRoute, busStop.latitude, busStop.longitude, System.currentTimeMillis()))
      newVehicleActor ! liveSourceLine
    }
    cleanUpLiveActorsList()
    //Update variables
    LiveStreamingCoordinatorImpl.numberLiveActors = liveActors.size
    LiveStreamingCoordinatorImpl.numberLiveChildren = context.children.size
  }


  /**
   * Periodically clean up the list of live actors to remove those that have not had activity recently (probably withdrawn)
   */
  private def cleanUpLiveActorsList() = {
    val cutOffThreshold = System.currentTimeMillis() - LiveStreamingCoordinatorImpl.IDLE_TIME_UNTIL_ACTOR_KILLED
    val actorsToKill = liveActors.filter(x => x._2.lastUpdated < cutOffThreshold)
    actorsToKill.foreach(x => {
      self ! new KillMessage(x._1, x._2.busRoute, x._2.lastLatitude, x._2.lastLongitude) //Kill actor
    })
  }

  /**
   * Kills an actor by sending it a poison pill and sending message to clients to remove
   * @param km The kill message containing vehicle ID and route
   */
  private def killActor(km: KillMessage) = {
    val value = liveActors.get(km.vehicleID)
    if (value.isDefined) value.get.actorRef ! PoisonPill
    LiveStreamingCoordinatorImpl.pushToClients(new PackagedStreamObject(km.vehicleID, "kill", Array((km.lastLatitude.toString, km.lastLongitude.toString, "0","0")), km.busRoute.routeID, "0", "0", "0", "0")) //Send kill to stream Queue so this is updated for clients
  }

}
