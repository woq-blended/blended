package blended.streams.processor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.Sink
import blended.util.logging.Logger

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.Success

object  Collector {

  def apply[T](name : String)(collected : T => Unit)(implicit system : ActorSystem, clazz : ClassTag[T]) : Collector[T] = {
    val p = Promise[List[T]]
    val actor = system.actorOf(CollectingActor.props[T](name, p)(collected))
    Collector(name = name, result = p.future, sink = Sink.actorRef[T](actor, CollectingActor.Completed), actor = actor)
  }
}

case class Collector[T] (
  name : String,
  result : Future[List[T]],
  sink : Sink[T, _],
  actor : ActorRef
)

object CollectingActor {
  object Completed
  object GetMessages

  def props[T](
    name: String, promise : Promise[List[T]]
  )(collected : T => Unit)(implicit clazz : ClassTag[T]) : Props =
    Props(new CollectingActor[T](name, promise)(collected))
}

class CollectingActor[T](
  name: String,
  promise: Promise[List[T]]
)(
  collected : T => Unit
)(implicit clazz : ClassTag[T]) extends Actor {

  private val log = Logger(getClass().getName())
  private val messages = mutable.Buffer.empty[T]

  override def receive: Receive = {

    case CollectingActor.GetMessages =>
      sender() ! messages.toList

    case CollectingActor.Completed =>
      log.debug(s"Completing Collector [$name] with [${messages.size}] messages.")
      promise.complete(Success(messages.toList))

    case msg : T =>
      log.trace(s"Collector [$name] received [$msg]")
      collected(msg)
      messages += msg.asInstanceOf[T]

    case m => log.warn(s"Received unhandled message [$m]")
  }

}
