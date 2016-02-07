package controllers.interfaces

import database.tfl.TFLInsertPointToPointDurationSupervisor
import processes.tfl.TFLProcessSourceLines
import streaming.LiveStreamingCoordinatorImpl

/**
 * User Control Interface for the Historical Data Collection
 */
object LiveStreamingControlInterface extends StartStopControlInterface {


  override def start(): Unit = {
    if (!started) {
      started = true
      println("Live Streaming turned on")
      LiveStreamingCoordinatorImpl.start()
    }
  }

  override def stop(): Unit = {
    if (started) {
      started = false
      println("Live Streaming turned off")
      LiveStreamingCoordinatorImpl.stop()
    }
  }

  def getNumberLiveActors  = LiveStreamingCoordinatorImpl.getNumberLiveActors
  def getNumberLiveChildren = LiveStreamingCoordinatorImpl.getNumberLiveChildren
}
