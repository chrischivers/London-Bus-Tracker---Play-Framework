package database

import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoCollection, MongoClient}
import play.api.Logger

/**
 * Database Objects
 */
object MongoDatabase {

  val dBName: String = "PredictionDB"

  lazy val mc: MongoClient = MongoClient()

  lazy val getDatabase = mc(dBName)

  def getCollection(dbc:DatabaseCollections): MongoCollection = {
    val coll = getDatabase(dbc.collectionName)
    createIndex(coll, dbc)
    Logger.info("Index Info: " + coll.getIndexInfo)
    coll
  }

  def closeConnection() = mc.close()

  def createIndex(mongoCollection: MongoCollection, dbc: DatabaseCollections) = {
    if (dbc.uniqueIndex) mongoCollection.createIndex(MongoDBObject(dbc.indexKeyList),MongoDBObject("unique" -> true))
    else mongoCollection.createIndex(MongoDBObject(dbc.indexKeyList),MongoDBObject("unique" -> false))
  }
}
