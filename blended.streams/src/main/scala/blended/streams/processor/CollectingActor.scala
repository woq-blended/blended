package blended.streams.processor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.Sink
import blended.streams.processor.CollectingActor.CompleteOn
import blended.util.logging.Logger

import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.Success

object Collector {

  def apply[T](name : String)(collected : T => Unit)(implicit system : ActorSystem, clazz : ClassTag[T]) : Collector[T] = {
    val p = Promise[List[T]]
    val actor = system.actorOf(CollectingActor.props[T](name, p)(collected))
    Collector(name = name, result = p.future, sink = Sink.actorRef[T](actor, CollectingActor.Completed), actor = actor)
  }
}

case class Collector[T](
  name : String,
  result : Future[List[T]],
  sink : Sink[T, _],
  actor : ActorRef
)

object CollectingActor {
  object Completed
  object GetMessages
  case class CompleteOn[T](f : Seq[T] => Boolean)

  def props[T](
    name : String, promise : Promise[List[T]]
  )(collected : T => Unit)(implicit clazz : ClassTag[T]) : Props =
    Props(new CollectingActor[T](name, promise)(collected))
}

class CollectingActor[T](
  name : String,
  promise : Promise[List[T]]
)(
  collected : T => Unit
)(implicit clazz : ClassTag[T]) extends Actor {

  private val log = Logger(getClass().getName())

  override def preStart(): Unit = context.become(working(Seq.empty, _ => false))

  override def receive: Receive = Actor.emptyBehavior

  private def complete(msgs: Seq[T]) : Unit = {
    log.info(s"Completing Collector [$name] with [${msgs.size}] messages.")
    promise.complete(Success(msgs.toList))
    context.stop(self)
  }

  private def working(msgs : Seq[T], isComplete : Seq[T] => Boolean) : Receive = {

    case c : CompleteOn[T] =>
      context.become(working(msgs, c.f))

    case CollectingActor.GetMessages =>
      sender() ! msgs.toList

    case CollectingActor.Completed =>
      complete(msgs)

    case msg : T =>
      log.trace(s"Collector [$name] received [$msg]")

      val newSeq : Seq[T] = msgs :+ msg
      collected(msg)

      if (isComplete(newSeq)) {
        complete(newSeq)
      } else {
        context.become(working(newSeq, isComplete))
      }

    case m => log.error(s"Received unhandled message [$m]")
  }

}
