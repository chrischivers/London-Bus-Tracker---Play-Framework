package datadefinitions

import akka.actor.ActorSystem
import controllers.Assets
import play.Play
import play.api.Routes

import scala.io.{BufferedSource, Source}


trait ResourceOperations {

  val actorResourcesSystem = ActorSystem("ResourcesActorSystem")

  val DEFAULT_ROUTE_LIST_FILE = Source.fromFile(Play.application().getFile("/public/data/routeList.csv"))
  val DEFAULT_ROUTE_DEF_FILE = Source.fromFile(Play.application().getFile("/public/data/busSequences.csv"))
  val DEFAULT_ROUTE_IGNORE_LIST_FILE =  Source.fromFile(Play.application().getFile("/public/data/routeIgnoreList.csv"))
  val DEFAULT_STOP_IGNORE_LIST_FILE = Source.fromFile(Play.application().getFile("/public/data/stopIgnoreList.csv"))
  val DEFAULT_LOAD_USING_HTML_METHOD_FILE = Source.fromFile(Play.application().getFile("/public/data/routesToGetUsingHTMLMethod.csv"))
  val DEFAULT_PUBLIC_HOLIDAY_LIST_FILE = Source.fromFile(Play.application().getFile("/public/data/publicHolidayList.csv"))

}
