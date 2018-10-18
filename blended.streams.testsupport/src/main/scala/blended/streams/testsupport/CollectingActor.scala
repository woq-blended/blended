package blended.streams.testsupport

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import blended.util.logging.Logger

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

object CollectingActor {
  object Completed
  object GetMessages

  def props[T](name: String, cbActor : ActorRef)(implicit clazz : ClassTag[T]) : Props =
    Props(new CollectingActor[T](name, cbActor))
}

class CollectingActor[T](name: String, cbActor: ActorRef)(implicit clazz : ClassTag[T]) extends Actor {

  private val log = Logger[CollectingActor[T]]
  private val messages : mutable.Buffer[T] = mutable.Buffer.empty

  override def receive: Receive = {

    case msg if msg.getClass() == clazz.runtimeClass =>
      messages += msg.asInstanceOf[T]

    case CollectingActor.GetMessages =>
      sender() ! messages.toList

    case CollectingActor.Completed =>
      cbActor ! messages.toList
  }

}
