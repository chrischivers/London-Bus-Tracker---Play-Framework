import filters.BasicAuthFilter
import org.atmosphere.play.AtmosphereCoordinator._
import org.atmosphere.play.Router
import play.api.mvc.{WithFilters, Handler, RequestHeader}
import play.api.{Application, GlobalSettings}
import streaming.Atmosphere

/**
 * Created by chrischivers on 09/02/16.
 */
object Global extends WithFilters(BasicAuthFilter) {

  override def onStart(app: Application): Unit = instance().discover(classOf[Atmosphere]).ready()

  override def onStop(app: Application): Unit = instance().shutdown()

  override def onRouteRequest(request: RequestHeader): Option[Handler] = Router.dispatch(request) match {
    case Some(result) => Some(result)
    case None => super.onRouteRequest(request)
  }
}

