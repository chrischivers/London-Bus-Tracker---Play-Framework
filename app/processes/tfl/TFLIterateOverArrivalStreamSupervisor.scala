package processes.tfl

import java.util.concurrent.TimeoutException

import akka.actor.SupervisorStrategy._
import akka.actor.{ OneForOneStrategy, Props, Actor}
import datasource.{TFLDataSourceImpl, SourceIterator, HttpDataStreamImpl}
import play.api.Logger
import play.api.libs.concurrent.Akka
import processes.ProcessingInterface
import play.api.Play.current


final case class Start()
final case class Stop()
final case class Next()

class TFLIterateOverArrivalStreamSupervisor extends Actor {

  val iteratingActor = context.actorOf(Props[IteratingActor])
  val monitoringActor = context.actorOf(Props[MonitoringActor])

  def receive = {
    case  Start=>
      Logger.info("Supervisor starting the iterating actor")
      iteratingActor ! Start
      iteratingActor ! Next
      monitoringActor ! Start
      monitoringActor ! Next
    case Stop =>
      iteratingActor ! Stop
      monitoringActor ! Stop
  }

  /**
   * Supervisers the Actor, ensuring that it restarts if it ctrashes
   */
  override val supervisorStrategy =
    OneForOneStrategy(loggingEnabled = false) {
      case e: TimeoutException =>
        Logger.debug("Incoming Stream TimeOut Exception. Restarting...")
        Thread.sleep(5000)
        TFLIterateOverArrivalStreamSupervisor.numberProcessedSinceRestart = 0
        Restart
      case e: Exception =>
        Logger.debug("Exception. Incoming Stream Exception. Restarting...")
        e.printStackTrace()
        Thread.sleep(5000)
        TFLIterateOverArrivalStreamSupervisor.numberProcessedSinceRestart = 0
        Restart
      case t =>
        super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)
    }

}


object TFLIterateOverArrivalStreamSupervisor extends ProcessingInterface {
  @volatile var numberProcessed:Long = 0
  @volatile var numberProcessedSinceRestart:Long = 0
  private val httpDataStream = new HttpDataStreamImpl(TFLDataSourceImpl)
  private val sourceIterator = new SourceIterator(httpDataStream)

  val supervisor = Akka.system.actorOf(Props[TFLIterateOverArrivalStreamSupervisor], name = "TFLIterateOverArrivalStreamSupervisor")


  override def start(): Unit = {
    Logger.info("Starting Interate Supervisor")
    supervisor ! Start

  }

  override def stop(): Unit = {
    Logger.info("Stopping Interate Supervisor")
    supervisor ! Stop
  }

  def getSourceIterator: Iterator[String] = {
    Logger.debug("Getting HTTP Source Iterator")
        sourceIterator.iterator

  }

  def closeDataStream() = {
    httpDataStream.closeStream()
  }

}

