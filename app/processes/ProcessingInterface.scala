package processes

import akka.actor.{ActorSystem, Props, Actor}

trait ProcessingInterface {

  val actorProcessingSystem = ActorSystem("ProcessingSystem")

  def start()
  def stop()

}
