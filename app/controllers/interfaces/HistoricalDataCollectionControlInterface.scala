package controllers.interfaces

import database.tfl.TFLInsertPointToPointDurationSupervisor
import processes.tfl.TFLProcessSourceLines

/**
 * User Control Interface for the Historical Data Collection
 */
object HistoricalDataCollectionControlInterface extends StartStopControlInterface {


  override def start(): Unit = {
    if (!started) {
      started = true
      println("Historical Data Collection turned on")
      TFLProcessSourceLines.setHistoricalDataStoring(true)
    }
  }

  override def stop(): Unit = {
    if (started) {
      started = false
      println("Historical Data Collection turned off")
      TFLProcessSourceLines.setHistoricalDataStoring(false)
    }
  }


  def getNumberInHoldingBuffer = TFLProcessSourceLines.getBufferSize

  def getNumberNonMatches = TFLProcessSourceLines.numberNonMatches

  def getNumberDBTransactionsRequested = TFLInsertPointToPointDurationSupervisor.numberDBTransactionsRequested

  def getNumberDBTransactionsExecuted = TFLInsertPointToPointDurationSupervisor.numberDBTransactionsExecuted

  def getNumberDBTransactionsOutstanding = (TFLInsertPointToPointDurationSupervisor.numberDBTransactionsRequested - TFLInsertPointToPointDurationSupervisor.numberDBTransactionsExecuted)

  def getNumberDBTransactionsDroppedDueToOverflow = TFLInsertPointToPointDurationSupervisor.numberDBTransactionsDroppedDueToOverflow

  def getNumberRecordsPulledFromDBRequested = TFLInsertPointToPointDurationSupervisor.numberRecordsPulledFromDbRequested

  def getNumberRecordsPulledFromDBExecuted = TFLInsertPointToPointDurationSupervisor.numberRecordsPulledFromDbExecuted

}
