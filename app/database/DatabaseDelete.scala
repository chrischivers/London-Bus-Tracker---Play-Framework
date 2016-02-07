package database

import org.bson.types.ObjectId

/**
 * Interface for deletions from the database
 */
trait DatabaseDelete extends DatabaseTransaction{

  def deleteDocument(docID: ObjectId): Unit = {
    supervisor ! docID
  }

}
