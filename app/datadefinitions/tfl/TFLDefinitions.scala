package datadefinitions.tfl

import java.util.Date
import datadefinitions.DataDefinitions
import datadefinitions.tfl.loadresources._
import datadefinitions.tools.FetchPolyLines

case class StopDefinitionFields(stopPointName:String, stopPointType:String, towards:String, bearing:Int, stopPointIndicator:String, stopPointState:Int, latitude:String, longitude:String)

/**
 * The reference for definitions files
 */
object TFLDefinitions extends DataDefinitions{

  override lazy val RouteDefinitionMap:Map[(String, Int), List[(Int, String, Option[String], String)]] =  LoadRouteDefinitions.getRouteDefinitionMap
  override  lazy val PointDefinitionsMap: Map[String,StopDefinitionFields] = LoadStopDefinitions.getStopDefinitionMap
  lazy val RouteIgnoreList: Set[String] = LoadRouteIgnoreList.routeIgnoreSet
  lazy val StopIgnoreList: Set[String] = LoadStopIgnoreList.stopIgnoreSet
  lazy val PublicHolidayList:List[Date] = LoadPublicHolidayList.publicHolidayList

  lazy val RouteList = getRouteList
  lazy val RouteListWithFirstLaststop = getRouteListWithFirstLastStops

  def updateRouteDefinitionsFromWeb() = {
    LoadRouteDefinitions.updateFromWeb()
  }

  def updateStopDefinitionsFromWeb() = {
    LoadStopDefinitions.updateFromWeb()
  }

  def addPolyLinesFromWeb() = {
    FetchPolyLines.updateAll()
  }

  /**
   * This gets the Route List from the Route Definiiton Map. As there are a mix of numbers and letters, the numbers and letters are partitioned, sorted separately and then joined
   * @return A list of sorted Routes
   */
  private def getRouteList:List[String] = {
    val list = TFLDefinitions.RouteDefinitionMap.map(x => x._1._1).toSet.toList
    val partitionedList = list.partition(x => !x.charAt(0).isLetter)
    val sortedIntList = partitionedList._1.map(_.toInt).sorted
    val sortedStringList = partitionedList._2.sorted
    sortedIntList.map(_.toString) ++ sortedStringList
  }

  private def getRouteListWithFirstLastStops:List[(String, String, String)] = {
    val list = TFLDefinitions.RouteDefinitionMap.map(x => x._1._1).toSet.toList
    val partitionedList = list.partition(x => !x.charAt(0).isLetter)
    val sortedIntList = partitionedList._1.map(_.toInt).sorted
    val sortedStringList = partitionedList._2.sorted
    val fullSortedList = sortedIntList.map(_.toString) ++ sortedStringList
    fullSortedList.map(x => (x, PointDefinitionsMap.get(RouteDefinitionMap.get(x,1).get.minBy(_._1)._2).get.stopPointName, PointDefinitionsMap.get(RouteDefinitionMap.get(x,1).get.maxBy(_._1)._2).get.stopPointName))
  }
}