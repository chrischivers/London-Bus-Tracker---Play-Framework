package datadefinitions

import akka.actor.ActorSystem
import play.Play

import scala.io.{BufferedSource, Source}


trait ResourceOperations {

  val actorResourcesSystem = ActorSystem("ResourcesActorSystem")

  val DEFAULT_ROUTE_LIST_FILE = Source.fromFile(Play.application().getFile("/resources/routeList.csv"))
  val DEFAULT_ROUTE_DEF_FILE = Source.fromFile(Play.application().getFile("/resources/busSequences.csv"))
  val DEFAULT_ROUTE_IGNORE_LIST_FILE =  Source.fromFile(Play.application().getFile("/resources/routeIgnoreList.csv"))
  val DEFAULT_STOP_IGNORE_LIST_FILE = Source.fromFile(Play.application().getFile("/resources/stopIgnoreList.csv"))
  val DEFAULT_LOAD_USING_HTML_METHOD_FILE = Source.fromFile(Play.application().getFile("/resources/routesToGetUsingHTMLMethod.csv"))
  val DEFAULT_PUBLIC_HOLIDAY_LIST_FILE = Source.fromFile(Play.application().getFile("/resources/publicHolidayList.csv"))

}
