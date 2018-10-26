package blended.streams.testsupport

import akka.actor.{Actor, ActorRef, Props}
import blended.util.logging.Logger

import scala.collection.mutable
import scala.reflect.ClassTag

object CollectingActor {
  object Completed
  object GetMessages

  def props[T](name: String, cbActor : ActorRef)(implicit clazz : ClassTag[T]) : Props =
    Props(new CollectingActor[T](name, cbActor))
}

class CollectingActor[T](name: String, cbActor: ActorRef)(implicit clazz : ClassTag[T]) extends Actor {

  private val log = Logger(getClass().getName())
  private val messages = mutable.Buffer.empty[T]

  def matching(o : Any) : Boolean = {
    val c1 = clazz.runtimeClass
    val c2 = o.getClass
    clazz.runtimeClass.isAssignableFrom(o.getClass())
  }

  override def receive: Receive = {

    case CollectingActor.GetMessages =>
      sender() ! messages.toList

    case CollectingActor.Completed =>
      log.info(s"Completing Collector [$name] with [${messages.size}] messages to [${cbActor.path}]")
      cbActor ! messages.toList

    case msg if matching(msg) =>
      log.info(s"Collector [$name] received [$msg]")
      messages += msg.asInstanceOf[T]

    case m => log.error(s"Received unhandled message [$m]")
  }

}
