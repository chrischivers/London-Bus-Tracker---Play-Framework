package datadefinitions.tfl.loadresources

import datadefinitions.LoadResourceFromSource
import play.api.Logger

object LoadRouteIgnoreList extends LoadResourceFromSource {

  override val bufferedSource = DEFAULT_ROUTE_IGNORE_LIST_FILE

  lazy val routeIgnoreSet:Set[String] = {
    var routeIgnoreSet:Set[String] = Set()
    bufferedSource.getLines().drop(1).foreach((line) => {
      //drop first row and iterate through others
      try {
        val splitLine = line.split(",")
        routeIgnoreSet += splitLine(0)
      }
      catch {
        case e: Exception =>
          Logger.error("Error reading route ignore list file. Error on line: " + line)
          throw new Exception("Error reading route ignore list file. Error on line: " + line)
      }
    })
    Logger.info("Route Ignore List Loaded")
    routeIgnoreSet
  }
}
