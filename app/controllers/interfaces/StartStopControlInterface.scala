package controllers.interfaces

/**
 * Interface for User Interfaces that use a Start/Stop Button
 */
trait StartStopControlInterface  {

  var started = false
  
  def start()
  def stop()


}
