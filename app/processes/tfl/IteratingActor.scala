package processes.tfl

import akka.actor.Actor
import datasource.tfl.TFLSourceLineFormatterImpl
import play.api.Logger
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Await, Future}
import ExecutionContext.Implicits.global

/**
 * Actor that iterates over live stream sending lines to be processed. On crash, the supervisor strategy restarts it
 */
class IteratingActor extends Actor {

  // Iterating pattern for this actor based on code snippet posted on StackOverflow
  //http://stackoverflow.com/questions/5626285/pattern-for-interruptible-loops-using-actors
  override def receive: Receive = inactive // Start out as inactive

  def inactive: Receive = { // This is the behavior when inactive
    case Start =>
      val x = TFLIterateOverArrivalStreamSupervisor.getSourceIterator
      Logger.info("Iterating Actor becoming active")
      context.become(active(x))

  }

  def active(it: Iterator[String]): Receive = { // This is the behavior when it's active
    case Stop =>
      context.become(inactive)
      TFLIterateOverArrivalStreamSupervisor.closeDataStream()
      Logger.info("Closing data stream")
    case Next =>
        val lineFuture = Future(TFLSourceLineFormatterImpl(it.next()))
        val line = Await.result(lineFuture, 10 seconds)
        TFLProcessSourceLines(line)
        TFLIterateOverArrivalStreamSupervisor.numberProcessed += 1
      TFLIterateOverArrivalStreamSupervisor.numberProcessedSinceRestart += 1
        self ! Next
      }

  override def postRestart(reason: Throwable): Unit = {
    Logger.debug("Iterating Actor Restarting")
    self ! Start
    self ! Next
  }


}
