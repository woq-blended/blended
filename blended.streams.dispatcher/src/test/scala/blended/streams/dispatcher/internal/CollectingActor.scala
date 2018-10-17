package blended.streams.dispatcher.internal

import akka.actor.{Actor, ActorRef, Props}
import blended.util.logging.Logger

import scala.collection.mutable
import scala.reflect.ClassTag

object CollectingActor {
  case object Completed

  def apply[T](name: String, cbActor : ActorRef)(implicit clazz : ClassTag[T]) : Props =
    Props(new CollectingActor[T](name, cbActor))
}

class CollectingActor[T](name: String, cbActor: ActorRef)(implicit clazz : ClassTag[T]) extends Actor {

  private val log = Logger[CollectingActor[T]]
  private val messages : mutable.Buffer[T] = mutable.Buffer.empty

  override def receive: Receive = {

    case msg if msg.getClass() == clazz.runtimeClass =>
      messages += msg.asInstanceOf[T]
    case CollectingActor.Completed =>
      cbActor ! messages.toList
  }
}
