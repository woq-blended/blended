package blended.streams.testsupport

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import blended.util.logging.Logger

import scala.collection.mutable
import scala.reflect.ClassTag

object  Collector {

  def apply[T](name : String)(implicit system : ActorSystem, clazz : ClassTag[T]) : Collector[T] = {
    val p = TestProbe(name)
    val actor = system.actorOf(CollectingActor.props[T](name, p.ref))
    Collector(probe = p, sink = Sink.actorRef[T](actor, CollectingActor.Completed), actor = actor)
  }
}

case class Collector[T] (
  probe : TestProbe,
  sink : Sink[T, _],
  actor : ActorRef
)

object CollectingActor {
  object Completed
  object GetMessages

  def props[T](name: String, cbActor : ActorRef)(implicit clazz : ClassTag[T]) : Props =
    Props(new CollectingActor[T](name, cbActor))


}

class CollectingActor[T](name: String, cbActor: ActorRef)(implicit clazz : ClassTag[T]) extends Actor {

  private val log = Logger(getClass().getName())
  private val messages = mutable.Buffer.empty[T]


  override def receive: Receive = {

    case CollectingActor.GetMessages =>
      sender() ! messages.toList

    case CollectingActor.Completed =>
      log.info(s"Completing Collector [$name] with [${messages.size}] messages to [${cbActor.path}]")
      cbActor ! messages.toList

    case msg : T =>
      log.trace(s"Collector [$name] received [$msg]")
      messages += msg.asInstanceOf[T]

    case m => log.error(s"Received unhandled message [$m]")
  }

}
