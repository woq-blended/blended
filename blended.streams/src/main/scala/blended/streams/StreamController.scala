package blended.streams

import akka.Done
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitch, KillSwitches, Materializer}
import blended.util.config.Implicits._
import blended.util.logging.Logger
import com.typesafe.config.Config

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object StreamControllerConfig {

  def fromConfig(cfg : Config) : Try[StreamControllerConfig] = Try {
    val minDelay : FiniteDuration = cfg.getDuration("minDelay", 5.seconds)
    val maxDelay : FiniteDuration = cfg.getDuration("maxDelay", 1.minute)
    val exponential : Boolean = cfg.getBoolean("exponential", true)
    val random : Double = cfg.getDouble("random", 0.2)
    val onFailure : Boolean = cfg.getBoolean("onFailureOnly", true)

    StreamControllerConfig(
      name = "",
      minDelay = minDelay,
      maxDelay = maxDelay,
      exponential = exponential,
      onFailureOnly = onFailure,
      random = random
    )
  }
}

case class StreamControllerConfig(
  name : String,
  minDelay : FiniteDuration,
  maxDelay : FiniteDuration,
  exponential : Boolean,
  onFailureOnly : Boolean,
  random : Double
)

object StreamController {

  case object Start
  case object Stop
  case class Abort(t: Throwable)
  case class StreamTerminated(exception : Option[Throwable])

  def props[T](src : Source[T, _], streamCfg : StreamControllerConfig)(implicit system : ActorSystem, materializer: Materializer) : Props =
    Props(new AbstractStreamController[T](streamCfg) {
      override def source(): Source[T, _] = src
    })
}

abstract class AbstractStreamController[T](streamCfg: StreamControllerConfig)(implicit system : ActorSystem, materializer: Materializer)
  extends Actor
  with StreamControllerSupport[T] {

  private[this] val log = Logger(getClass().getName())
  private[this] implicit val eCtxt : ExecutionContext = context.system.dispatcher

  override def preStart(): Unit = self ! StreamController.Start

  override def receive: Receive = starting(streamCfg, streamCfg.minDelay)

  override def toString: String = s"${getClass().getSimpleName()}($streamCfg)"
}
