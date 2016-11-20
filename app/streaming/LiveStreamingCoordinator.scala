package streaming

import akka.actor.Props
import datasource.SourceLine
import play.api.libs.concurrent.Akka
import processes.tfl.TFLProcessSourceLines
import play.api.Play.current


trait LiveStreamingCoordinator {

  val vehicleSupervisor = Akka.system.actorOf(Props[LiveVehicleSupervisor], "VehicleSupervisor")

  //val server = actorServerSystem.actorOf(WebSocketServer.props(), "websocket")

  @volatile var numberLiveActors = 0
  @volatile var numberLiveChildren = 0

  //implicit val timeout = 1000
  val CACHE_HOLD_FOR_TIME:Int
  val IDLE_TIME_UNTIL_ACTOR_KILLED:Int


  def processSourceLine(liveSourceLine: SourceLine) = vehicleSupervisor ! liveSourceLine

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
