package controllers

import com.typesafe.plugin._
import commons.ReadProperties
import play.api.Logger
import play.api.mvc._

object EmailAlertsController extends Controller {

  var alertsEnabled = false
  var numberEmailsSent = 0
  val emailerFromAddress = ReadProperties.getProperty("emailerfromaddress")
  val emailerToAddress = ReadProperties.getProperty("emailertoaddress")

  def isStarted = Action {
    Ok(alertsEnabled.toString)
  }

  def turnOnEmailAlerts = Action {
    println ("Email Alerts On")
    alertsEnabled = true
    Ok("started")
  }

  def turnOffEmailAlerts = Action {
    println ("Email Alerts Off")
    alertsEnabled = false
    Ok("stopped")
  }

   def sendAlert(alertText:String) = {
    if (alertsEnabled) {
      println("Email alert being sent")
      sendMesage(alertText)
      numberEmailsSent += 1
    }
  }

  def sendMesage(emailText:String) = {
    val mail = use[MailerPlugin].email
    mail.setSubject("BBKProject - Server Alert")
    mail.setRecipient(emailerToAddress)
    mail.setFrom(emailerFromAddress)
    //or use a list
    //mail.setBcc(List("Dummy <example@example.org>", "Dummy2 <example@example.org>"):_*)
    //mail.setFrom("Miles Davenport <miles.davenport@anotheremail.com>")
    //adds attachment
    //mail.addAttachment("attachment.pdf", new File("/some/path/attachment.pdf"))
    //adds inline attachment from byte array
    //val data: Array[Byte] = "data".getBytes
    //mail.addAttachment("data.txt", data, "text/plain", "A simple file", EmailAttachment.INLINE)
    //sends html
    //mail.sendHtml("<html>html</html>" )
    //sends text/text
    mail.send(emailText)
    //sends both text and html
    //mail.send( "text", "<html>html</html>")
    Logger.info("Server Email sent :" + emailText)
  }


}