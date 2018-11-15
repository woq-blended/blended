package blended.streams

import akka.NotUsed
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitch, KillSwitches, Materializer}
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Random, Success}

case class StreamControllerConfig(
  name : String,
  source : Source[FlowEnvelope, NotUsed],
  minDelay : FiniteDuration = 5.seconds,
  maxDelay : FiniteDuration = 1.minute,
  exponential : Boolean = true,
  onFailureOnly : Boolean = true,
  random : Double = 0.2
)

object StreamController {

  case object Start
  case object Stop
  case class Abort(t: Throwable)
  case class StreamTerminated(exception : Option[Throwable])

  def props(streamCfg : StreamControllerConfig)(implicit system : ActorSystem, materializer: Materializer) : Props = Props(new StreamController(streamCfg))
}

class StreamController(streamCfg: StreamControllerConfig)(implicit system : ActorSystem, materializer: Materializer) extends Actor {

  private[this] val log = Logger[StreamController]
  private[this] implicit val eCtxt = context.system.dispatcher
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
      if (streamCfg.exponential) interval.toMillis * 2 else interval.toMillis + initialInterval.toMillis

    newIntervalMillis = scala.math.min(
      streamCfg.maxDelay.toMillis,
      newIntervalMillis + newIntervalMillis * noise
    )

    newIntervalMillis.toLong.millis
  }

  def starting : Receive = {
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
        log.debug(s"Stream [${streamCfg.name}] terminated...scheduling restart in [$interval]")

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
