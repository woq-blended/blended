package blended.streams.persistence

import akka.persistence.PersistentActor
import blended.streams.persistence.RestartableActor.{RestartActor, RestartActorException}

trait RestartableActor extends PersistentActor {

  def restartReceive : Receive = {
    case RestartActor => throw new RestartActorException
  }
}

object RestartableActor {

  case object RestartActor

  class RestartActorException extends Exception("Actor will be restarted intentionally, use in test only.")
}
