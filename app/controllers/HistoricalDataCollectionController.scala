package controllers

import database.tfl.TFLInsertPointToPointDurationSupervisor
import play.api.Logger
import play.api.mvc._
import processes.tfl.TFLProcessSourceLines

object HistoricalDataCollectionController extends Controller {

  var started = false

  def isStarted = Action {
    Ok(started.toString)
  }
  

  def turnOnHistoricalDataCollection = Action {
    if (!started) {
      started = true
      Logger.info("Historical Data Collection turned on")
      TFLProcessSourceLines.setHistoricalDataStoring(true)
    }
    Ok("started")
  }

  def turnOffHistoricalDataCollection = Action {
    if (started) {
      started = false
      Logger.info("Historical Data Collection turned off")
      TFLProcessSourceLines.setHistoricalDataStoring(false)
    }
    Ok("stopped")
  }

  def getSizeOfHoldingBuffer = Action {
    Ok(TFLProcessSourceLines.getBufferSize.toString)
  }

  def getNumberNonMatches = Action {
    Ok(TFLProcessSourceLines.numberNonMatches.toString)
  }

  def getInsertTransactionsRequested = Action {
    Ok(TFLInsertPointToPointDurationSupervisor.numberDBTransactionsRequested.toString)
  }

  def getInsertTransactionsExecuted = Action {
    Ok(TFLInsertPointToPointDurationSupervisor.numberDBTransactionsExecuted.toString)
  }
  def getInsertTransactionsOutstanding = Action {
    Ok((TFLInsertPointToPointDurationSupervisor.numberDBTransactionsRequested - TFLInsertPointToPointDurationSupervisor.numberDBTransactionsExecuted).toString)
  }

  def getInsertTransactionsDropped = Action {
    Ok(TFLInsertPointToPointDurationSupervisor.numberDBTransactionsDroppedDueToOverflow.toString)
  }

  def getRecordPullsRequested = Action {
    Ok(TFLInsertPointToPointDurationSupervisor.numberRecordsPulledFromDbRequested.toString)
  }

  def getRecordPullsExecuted = Action {
    Ok(TFLInsertPointToPointDurationSupervisor.numberRecordsPulledFromDbExecuted.toString)
  }

}


