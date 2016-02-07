package controllers

import controllers.interfaces.{HistoricalDataCollectionControlInterface, StreamProcessingControlInterface}
import play.api.mvc._

object HistoricalDataCollectionController extends Controller {

  def isStarted = Action {
    Ok(HistoricalDataCollectionControlInterface.started.toString)
  }
  

  def turnOnHistoricalDataCollection = Action {
    HistoricalDataCollectionControlInterface.start()
    Ok("started")
  }

  def turnOffHistoricalDataCollection = Action {
    HistoricalDataCollectionControlInterface.stop()
    Ok("stopped")
  }

  def getSizeOfHoldingBuffer = Action {
    Ok(HistoricalDataCollectionControlInterface.getNumberInHoldingBuffer.toString)
  }

  def getNumberNonMatches = Action {
    Ok(HistoricalDataCollectionControlInterface.getNumberNonMatches.toString)
  }

  def getInsertTransactionsRequested = Action {
    Ok(HistoricalDataCollectionControlInterface.getNumberDBTransactionsRequested.toString)
  }

  def getInsertTransactionsExecuted = Action {
    Ok(HistoricalDataCollectionControlInterface.getNumberDBTransactionsExecuted.toString)
  }
  def getInsertTransactionsOutstanding = Action {
    Ok(HistoricalDataCollectionControlInterface.getNumberDBTransactionsOutstanding.toString)
  }

  def getInsertTransactionsDropped = Action {
    Ok(HistoricalDataCollectionControlInterface.getNumberDBTransactionsDroppedDueToOverflow.toString)
  }

  def getRecordPullsRequested = Action {
    Ok(HistoricalDataCollectionControlInterface.getNumberRecordsPulledFromDBRequested.toString)
  }

  def getRecordPullsExecuted = Action {
    Ok(HistoricalDataCollectionControlInterface.getNumberRecordsPulledFromDBExecuted.toString)
  }

}
