package controllers

import play.api.mvc.{Action, Controller}

import scala.io.Source


object Application extends Controller  {

    def admin = Action {
      Ok(views.html.control())
    }


  def map = Action {
    Ok(views.html.livemap())
  }

  def prediction = Action {
    Ok(views.html.prediction())
  }

  def adminLog = Action {
    val listOfLines = Source.fromFile("logs/main.log").getLines().toList
    Ok(views.html.console(listOfLines))
  }

}

