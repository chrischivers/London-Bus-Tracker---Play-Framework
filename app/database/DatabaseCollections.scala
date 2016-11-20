package database

import akka.actor.ActorRef
import com.mongodb.casbah.MongoCollection

/*
 * Database Collection Objects
 */
trait DatabaseCollections {
  val collectionName: String
  val fieldsVector: Vector[String]
  val indexKeyList: List[(String,Int)]
  val uniqueIndex: Boolean

  val dBConnection: MongoCollection

  val supervisor: ActorRef

  @volatile var numberInsertsRequested:Long  = 0
  @volatile var numberInsertsCompleted:Long  = 0
  @volatile var numberGetRequests:Long = 0
  @volatile var numberDeleteRequests:Long = 0

  def incrementLogRequest(ilv: IncrementLogValues) = supervisor ! ilv

}


trait IncrementLogValues

case class IncrementNumberInsertsRequested(incrementBy: Int) extends IncrementLogValues
case class IncrementNumberInsertsCompleted(incrementBy: Int) extends IncrementLogValues
case class IncrementNumberGetRequests(incrementBy: Int) extends IncrementLogValues
case class IncrementNumberDeleteRequests(incrementBy: Int) extends IncrementLogValues

