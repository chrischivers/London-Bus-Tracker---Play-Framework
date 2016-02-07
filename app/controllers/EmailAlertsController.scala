package controllers

import controllers.StreamProcessorController._
import controllers.interfaces.{StreamProcessingControlInterface, EmailAlertInterface}
import play.api.mvc._

object EmailAlertsController extends Controller {

  def isStarted = Action {
    Ok(EmailAlertInterface.started.toString)
  }

  def turnOnEmailAlerts = Action {
    EmailAlertInterface.start()
    Ok("started")
  }

  def turnOffEmailAlerts = Action {
    EmailAlertInterface.stop()
    Ok("stopped")
  }



}
