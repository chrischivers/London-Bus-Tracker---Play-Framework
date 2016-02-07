package controllers

import play.api.mvc.{Action, Controller}

object Admin  extends Controller {

    def index = Action {
      Ok(views.html.control())
    }

}
