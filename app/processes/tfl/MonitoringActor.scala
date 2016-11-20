package processes.tfl

import akka.actor.Actor
import controllers.EmailAlertsController
import play.api.Logger


/**
 * Actor that monitors the streaming and detects when there is a problem.
 */
class MonitoringActor extends Actor {

  val linesNotBeingReadAlertText = "LINES NOT BEING READ AS EXPECTED. POSSIBLE SERVER CRASH."
  val freeMemoryLowAlertText = "FREE MEMORY RUNNING LOW"
  val periodToCheck = 600000
  val MIN_LINES_TO_READ_IN_PERIOD = 10
  val MEMORY_REMAINING_TO_ALERT = 200
  var timeStampLastChecked:Long = 0
  var linesReadOnLastCheck:Long = 0

  val TIME_TO_WAIT_BETWEEN_CHECKS = 10000

  val mb = 1024*1024
  val runtime = Runtime.getRuntime

  // Iterating pattern for this actor based on code snippet posted on StackOverflow
  //http://stackoverflow.com/questions/5626285/pattern-for-interruptible-loops-using-actors
  override def receive: Receive = inactive // Start out as inactive

  def inactive: Receive = { // This is the behavior when inactive
    case Start =>
      Logger.info("Monitoring Actor becoming active")
      context.become(active())

  }

  def active(): Receive = {
    // This is the behavior when it's active
    case Stop =>
      context.become(inactive)
    case Next =>

      val linesRead = TFLIterateOverArrivalStreamSupervisor.numberProcessed
      if (System.currentTimeMillis() - periodToCheck > timeStampLastChecked) {
        if (linesRead - linesReadOnLastCheck < MIN_LINES_TO_READ_IN_PERIOD && linesRead != 0) {
          Logger.error("No lines being read")
          EmailAlertsController.sendAlert(linesNotBeingReadAlertText)
        }
        timeStampLastChecked = System.currentTimeMillis()
        linesReadOnLastCheck = linesRead
      }
      /*if (((runtime.totalMemory - runtime.freeMemory) / mb)  - (runtime.maxMemory / mb) < MEMORY_REMAINING_TO_ALERT) {
        EmailAlertsController.sendAlert(freeMemoryLowAlertText)
      }*/
      Thread.sleep(TIME_TO_WAIT_BETWEEN_CHECKS)
      self ! Next
  }


  override def postRestart(reason: Throwable): Unit = {
    Logger.debug("Monitoring Actor Restarting")
    self ! Start
    self ! Next
  }
}
