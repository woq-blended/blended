package blended.streams.processor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.Sink
import blended.util.logging.Logger

import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.Success

object Collector {

  def apply[T](
    name: String,
    onCollected: Option[T => Boolean] = None,
    completeOn: Option[Seq[T] => Boolean] = None
  )(implicit
    system: ActorSystem,
    clazz: ClassTag[T]
  ): Collector[T] = {

    val result: Promise[List[T]] = Promise[List[T]]()

    val actor = system.actorOf(
      CollectingActor.props[T](
        name = name,
        promise = result,
        onCollected = (t: T) => onCollected.map(f => f(t)).getOrElse(true),
        completeOn = (s: Seq[T]) => completeOn.map(f => f(s)).getOrElse(false)
      )
    )

    Collector(
      name = name,
      result = result.future,
      sink = Sink.actorRef[T](actor, CollectingActor.Success, t => CollectingActor.Failed(t)),
      actor = actor
    )
  }
}

case class Collector[T](
  name: String,
  result: Future[List[T]],
  sink: Sink[T, _],
  actor: ActorRef
)

object CollectingActor {
  object Success
  case class Failed(t: Throwable)
  object GetMessages

  def props[T](
    name: String,
    promise: Promise[List[T]],
    completeOn: Seq[T] => Boolean,
    onCollected: T => Boolean
  )(implicit clazz: ClassTag[T]): Props =
    Props(new CollectingActor[T](name, promise, completeOn, onCollected))
}

class CollectingActor[T](
  name: String,
  promise: Promise[List[T]],
  completeOn: Seq[T] => Boolean,
  collected: T => Boolean
)(implicit clazz: ClassTag[T])
    extends Actor {

  private val log = Logger(getClass().getName())

  override def preStart(): Unit = context.become(working(Seq.empty, completeOn))

  override def receive: Receive = Actor.emptyBehavior

  private def complete(msgs: Seq[T]): Unit = {
    log.debug(s"Completing Collector [$name] with [${msgs.size}] messages.")
    promise.complete(Success(msgs.toList))
    context.stop(self)
  }

  private def working(msgs: Seq[T], isComplete: Seq[T] => Boolean): Receive = {

    case CollectingActor.GetMessages =>
      sender() ! msgs.toList

    case CollectingActor.Success =>
      complete(msgs)

    case CollectingActor.Failed(t) =>
      log.debug(s"Collecting actor ended with exception [${t.getMessage()}]")
      promise.failure(t)
      context.stop(self)

    case msg: T =>
      log.trace(s"Collector [$name] received [$msg]")

      val newSeq: Seq[T] = msgs ++ (if (collected(msg)) Seq(msg) else Seq.empty)

      if (isComplete(newSeq)) {
        log.info(s"Complete condition for [$name] is satisfied.")
        complete(newSeq)
      } else {
        context.become(working(newSeq, isComplete))
      }

    case m => log.warn(s"Received unhandled message [$m]")
  }
}
