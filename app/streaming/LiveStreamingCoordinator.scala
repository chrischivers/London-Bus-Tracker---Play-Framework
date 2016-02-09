package streaming

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import controllers.LiveStreamingController
import datasource.tfl.TFLSourceLineImpl
import processes.tfl.TFLProcessSourceLines


trait LiveStreamingCoordinator {

  val actorVehicleSystem = ActorSystem("VehicleSystem")
  val vehicleSupervisor = actorVehicleSystem.actorOf(Props[LiveVehicleSupervisor], "VehicleSupervisor")

  implicit val actorServerSystem =  ActorSystem("WebServeSystem")
  //val server = actorServerSystem.actorOf(WebSocketServer.props(), "websocket")

  @volatile var numberLiveActors = 0
  @volatile var numberLiveChildren = 0

  //implicit val timeout = 1000
  val CACHE_HOLD_FOR_TIME:Int
  val IDLE_TIME_UNTIL_ACTOR_KILLED:Int


  def processSourceLine(liveSourceLine: TFLSourceLineImpl) = vehicleSupervisor ! liveSourceLine

  def getNumberLiveActors = numberLiveActors

  def getNumberLiveChildren = numberLiveChildren

  /**
   * Pushes a packaged stream object to clients
   * @param pso The packaged stream objects
   */
  def pushToClients(pso: PackagedStreamObject) =  {
    WebSocketSupervisor.PushToChildren(pso)
  }

  def stop(): Unit = {
   // IO(UHttp) ! Http.Unbind
    TFLProcessSourceLines.setLiveStreamCollection(false)
  }

  /**
   * Binds server
   */
  def start(): Unit = {
  //  IO(UHttp) ! Http.Bind(server, interface = "0.0.0.0", port = 80)
    //IO(UHttp) ! Http.Bind(server, interface = "0.0.0.0", port = 8080)
    TFLProcessSourceLines.setLiveStreamCollection(true)
  }


}
