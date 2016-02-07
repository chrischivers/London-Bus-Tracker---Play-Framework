package datadefinitions.tools

import akka.actor.{ActorSystem, PoisonPill, Props, Actor}
import com.mongodb.casbah.Imports
import database.POINT_TO_POINT_COLLECTION
import database.tfl.{TFLDeletePointToPointDuration, TFLGetPointToPointDocument}
import datadefinitions.ResourceOperations
import datadefinitions.tfl.TFLDefinitions
import org.bson.types.ObjectId
import play.api.Logger

/**
 * Tool to look through PoinToPoint collection and remove any entries that no longer correspond to entries in the definition file
 */
object CleanPointToPointData  extends ResourceOperations {

  private val streamActor = actorResourcesSystem.actorOf(Props[CleanPointToPointData], name = "CleanPointToPointDataActor")
  @volatile var numberDocumentsRead = 0
  @volatile var numberDocumentsDeleted = 0

  def start() = {
    streamActor ! "start"
  }

  def stop() = {
    streamActor ! PoisonPill
  }

}

class CleanPointToPointData extends Actor {

  val collection = POINT_TO_POINT_COLLECTION
  val routeDefinitions = TFLDefinitions.RouteDefinitionMap
  val cursor = TFLGetPointToPointDocument.fetchAll()


  override def receive: Receive = {
    case "start" => self ! cursor.next()
    case doc:Imports.DBObject => processNext(doc)
  }


  def processNext(doc:Imports.DBObject): Unit = {
    CleanPointToPointData.numberDocumentsRead += 1
    val routeID = doc.get(collection.ROUTE_ID).asInstanceOf[String]
    val direction = doc.get(collection.DIRECTION_ID).asInstanceOf[Int]
    val fromStop = doc.get(collection.FROM_POINT_ID).asInstanceOf[String]
    val toStop = doc.get(collection.TO_POINT_ID).asInstanceOf[String]
   val id = doc.get("_id").asInstanceOf[ObjectId]

    if (canDelete(routeID,direction,fromStop,toStop))  {
      CleanPointToPointData.numberDocumentsDeleted += 1
      Logger.info("Deleting the following: Route: " + routeID + ". Direction: " + direction + ". From Stop: " + fromStop + ". To Stop: " + toStop)
      TFLDeletePointToPointDuration.deleteDocument(id)
    }

    if (cursor.hasNext) self ! cursor.next()
  }

  /**
   * Tests whether it is safe to delet
   * @param routeID The Route ID
   * @param direction The Direction ID
   * @param fromStop The From Stop ID
   * @param toStop The To Stop ID
   * @return A boolean indicating whether the record can be deleted
   */
  def canDelete(routeID:String, direction:Int, fromStop:String, toStop:String): Boolean = {

    val fromStopReference = routeDefinitions.get(routeID,direction).getOrElse(return true).filter(x => x._2 == fromStop)
    val toStopReference = routeDefinitions.get(routeID,direction).getOrElse(return true).filter(x => x._2 == toStop)
    if (fromStopReference.isEmpty || toStopReference.isEmpty) return true

    val fromPointSeqFromDef = fromStopReference.head._1
    val toPointSeqFromDef = toStopReference.last._1
    if(fromPointSeqFromDef + 1 != toPointSeqFromDef) return true

    false
  }
}