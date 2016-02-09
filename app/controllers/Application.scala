package controllers

import play.api.mvc.{WithFilters, Action, Controller}



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

}

