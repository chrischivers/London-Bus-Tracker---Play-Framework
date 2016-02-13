package processes

import akka.actor.{ActorSystem, Props, Actor}

trait ProcessingInterface {

  def start()
  def stop()

}
