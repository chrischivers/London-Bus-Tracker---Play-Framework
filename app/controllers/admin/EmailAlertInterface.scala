package controllers.admin

import controllers.admin.emailer.Emailer


object EmailAlertInterface extends StartStopControlInterface{

  var alertsEnabled = false
  var numberEmailsSent = 0


  def sendAlert(alertText:String) = {
    if (alertsEnabled) {
    //  Emailer.sendMesage(alertText)
      numberEmailsSent += 1
    }
  }

 // override def getVariableArray: Array[String] = Array(numberEmailsSent.toString)

  override def stop(): Unit = alertsEnabled = false
  
  override def start(): Unit = alertsEnabled = true
}
