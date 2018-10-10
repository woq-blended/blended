package blended.jms.bridge.internal

import akka.{Done, NotUsed}
import akka.actor.{Actor, Props}
import akka.stream.{ActorMaterializer, KillSwitch, KillSwitches}
import akka.stream.scaladsl.{Keep, Sink, Source}
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

private[bridge] case class StreamControllerConfig(

  name : String,
  stream : Source[FlowEnvelope, NotUsed],
  minDelay : FiniteDuration = 5.seconds,
  maxDelay : FiniteDuration = 1.minute,
  exponential : Boolean = true,
  onFailureOnly : Boolean = true,
  random : Double = 0.2
)

private[bridge] object StreamController {

  case object Start
  case object Stop
  case class Abort(t: Throwable)
  case class StreamTerminated(exception : Option[Throwable])

  def props(streamCfg : StreamControllerConfig) : Props = Props(new StreamController(streamCfg))
}

private[bridge] class StreamController(streamCfg: StreamControllerConfig) extends Actor {

  private[this] val log = Logger[StreamController]
  private[this] implicit val materializer = ActorMaterializer()
  private[this] implicit val eCtxt = context.system.dispatcher

  private[this] var interval : FiniteDuration = streamCfg.minDelay

  override def preStart(): Unit = self ! StreamController.Start

  override def receive: Receive = starting

  def starting : Receive = {
    case StreamController.Start =>
      log.debug(s"Initializing StreamController [${streamCfg.name}]")

      val (killswitch, done) = streamCfg.stream
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
        log.debug(s"Stream [${streamCfg.name}] terminated...scheduling restart in [${interval.toSeconds}s]")

        context.system.scheduler.scheduleOnce(interval, self, StreamController.Start)

        if (interval < streamCfg.maxDelay) {
          if (streamCfg.exponential) interval *= 2 else interval += interval
          interval = if (interval <= streamCfg.maxDelay) interval else streamCfg.maxDelay
        }

        context.become(starting)
      } else {
        context.stop(self)
      }
  }

  def stopping : Receive = {
    case StreamController.StreamTerminated(_) => context.stop(self)
  }

}
