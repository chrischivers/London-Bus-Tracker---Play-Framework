package controllers

import database.RouteSectionHistoryDB
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

  def getInsertTransactionsRequested = Action {
    Ok(RouteSectionHistoryDB.numberInsertsRequested.toString)
  }

  def getInsertTransactionsExecuted = Action {
    Ok(RouteSectionHistoryDB.numberInsertsCompleted.toString)
  }
  def getInsertTransactionsOutstanding = Action {
    Ok((RouteSectionHistoryDB.numberInsertsRequested - RouteSectionHistoryDB.numberInsertsCompleted).toString)
  }

 /* def getInsertTransactionsDropped = Action {
    Ok(TFLInsertPointToPointDurationSupervisor.numberDBTransactionsDroppedDueToOverflow.toString)
  }*/

  def getPruneRecordsRequested = Action {
    Ok(RouteSectionHistoryDB.numberPruneRecordsRequested.toString)
  }

  def getPruneRecordsCompleted = Action {
    Ok(RouteSectionHistoryDB.numberPruneRecordsCompleted.toString)
  }

}


