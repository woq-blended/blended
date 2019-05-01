package blended.streams

import akka.NotUsed
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitch, KillSwitches, Materializer}
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger
import com.typesafe.config.Config
import blended.util.config.Implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Random, Success, Try}

object StreamControllerConfig {

  def fromConfig[T](cfg : Config) : Try[StreamControllerConfig[T]] = Try {
    val minDelay : FiniteDuration = cfg.getDuration("minDelay", 5.seconds)
    val maxDelay : FiniteDuration = cfg.getDuration("maxDelay", 1.minute)
    val exponential : Boolean = cfg.getBoolean("exponential", true)
    val random : Double = cfg.getDouble("random", 0.2)
    val onFailure : Boolean = cfg.getBoolean("onFailureOnly", true)

    StreamControllerConfig[T](
      name = "",
      source = Source.empty,
      minDelay = minDelay,
      maxDelay = maxDelay,
      exponential = exponential,
      onFailureOnly = onFailure,
      random = random
    )
  }
}

case class StreamControllerConfig[T](
  name : String,
  source : Source[T, NotUsed],
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

  def props[T](streamCfg : StreamControllerConfig[T])(implicit system : ActorSystem, materializer: Materializer) : Props =
    Props(new StreamController[T](streamCfg))
}

class StreamController[T](streamCfg: StreamControllerConfig[T])(implicit system : ActorSystem, materializer: Materializer) extends Actor {

  private[this] val log = Logger[StreamController[T]]
  private[this] implicit val eCtxt : ExecutionContext = context.system.dispatcher
  private[this] val rnd = new Random()

  private[this] val initialInterval : FiniteDuration = streamCfg.minDelay
  private[this] var interval : FiniteDuration = streamCfg.minDelay

  override def preStart(): Unit = self ! StreamController.Start

  override def receive: Receive = starting

  override def toString: String = s"${getClass().getSimpleName()}($streamCfg)"

  private[this] def nextInterval : FiniteDuration = {

    val noise = {
      val d = rnd.nextDouble().abs
      (d - d.floor) / (1 / (streamCfg.random * 2)) - streamCfg.random
    }

    var newIntervalMillis : Double =
      if (streamCfg.exponential) {
        interval.toMillis * 2
      } else {
        interval.toMillis + initialInterval.toMillis
      }

    newIntervalMillis = scala.math.min(
      streamCfg.maxDelay.toMillis,
      newIntervalMillis + newIntervalMillis * noise
    )

    newIntervalMillis.toLong.millis
  }

  def starting : Receive = {
    case StreamController.Stop =>
      context.stop(self)

    case StreamController.Start =>
      log.debug(s"Initializing StreamController [${streamCfg.name}]")

      val (killswitch, done) = streamCfg.source
        .viaMat(KillSwitches.single)(Keep.right)
        .watchTermination()(Keep.both)
        .toMat(Sink.ignore)(Keep.left)
        .run()

      done.onComplete {
        case Success(_) =>
          self ! StreamController.StreamTerminated(None)
        case Failure(t) =>
          self ! StreamController.StreamTerminated(Some(t))
      }

      context.become(running(killswitch))
  }

  def running(killSwitch: KillSwitch) : Receive = {
    case StreamController.Stop =>
      killSwitch.shutdown()
      context.become(stopping)
    case StreamController.Abort(t) =>
      killSwitch.abort(t)
      context.become(stopping)

    case StreamController.StreamTerminated(t) =>
      if (t.isDefined || (!streamCfg.onFailureOnly)) {
        t.foreach { e =>
          log.error(e)(e.getMessage)
        }
        log.info(s"Stream [${streamCfg.name}] terminated [${t.map(_.getMessage).getOrElse("")}] ...scheduling restart in [$interval]")

        context.system.scheduler.scheduleOnce(interval, self, StreamController.Start)
        interval = nextInterval

        context.become(starting)
      } else {
        context.stop(self)
      }
  }

  def stopping : Receive = {
    case StreamController.StreamTerminated(_) => context.stop(self)
  }
}
