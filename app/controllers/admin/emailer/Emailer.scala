package controllers.admin.emailer

import javax.mail.internet.InternetAddress


import com.typesafe.plugin.MailerPlugin
import commons.ReadProperties
import com.typesafe.plugin._
import play.api.Play.current

object Emailer  {

  val emailerToAddress = ReadProperties.getProperty("emailertoaddress")

  /*def sendMesage(emailText:String) = {
    val mail = use[MailerPlugin].email
    mail.setSubject("BBKProject - Server Alert")
    mail.setRecipient(emailerToAddress)
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
    logger.info("Server Email sent :" + emailText)
  }*/


}


