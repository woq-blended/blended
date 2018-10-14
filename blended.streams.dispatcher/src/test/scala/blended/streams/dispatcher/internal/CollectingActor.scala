package blended.streams.dispatcher.internal

import akka.actor.{Actor, ActorRef, Props}
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.collection.mutable

object CollectingActor {
  case object Completed

  def apply(name: String, cbActor : ActorRef) : Props =
    Props(new CollectingActor(name, cbActor))
}

class CollectingActor(name: String, cbActor: ActorRef) extends Actor {

  private val log = Logger[CollectingActor]
  private val envelopes : mutable.Buffer[FlowEnvelope] = mutable.Buffer.empty

  override def receive: Receive = {

    case env: FlowEnvelope =>
      log.debug(s"Collecting Actor [$name] received envelope [$env]")
      envelopes += env
    case CollectingActor.Completed =>
      log.debug(s"Collecting Actor [$name] completed with [${envelopes.mkString(",")}]")
      cbActor ! envelopes.toList
  }
}
