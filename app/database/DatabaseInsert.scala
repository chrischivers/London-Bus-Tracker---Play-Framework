package database

/**
 * Interface for insertions to the database
 */
trait DatabaseInsert extends DatabaseTransaction{

  @volatile  var numberDBTransactionsRequested: Long = 0
  @volatile var numberDBTransactionsExecuted: Long = 0
  @volatile var numberDBTransactionsDroppedDueToOverflow: Long = 0
  val MAXIMUM_OUTSTANDING_TRANSACTIONS = 1000


  def insertDoc(doc: DatabaseDocument ) = supervisor ! doc
}
