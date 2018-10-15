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
      envelopes += env
    case CollectingActor.Completed =>
      cbActor ! envelopes.toList
  }
}
