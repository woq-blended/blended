package blended.streams

import akka.Done
import akka.actor.Actor
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitch, KillSwitches, Materializer}
import blended.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

trait StreamControllerSupport[T] { this : Actor =>

  private[this] val log : Logger = Logger(getClass().getName())
  private[this] val rnd = new Random()
  private[this] implicit val materializer : Materializer = ActorMaterializer()
  private[this] implicit val eCtxt : ExecutionContext = context.dispatcher

  val nextInterval : FiniteDuration => StreamControllerConfig => FiniteDuration = { interval => streamCfg =>

    val noise = {
      val d = rnd.nextDouble().abs
      (d - d.floor) / (1 / (streamCfg.random * 2)) - streamCfg.random
    }

    var newIntervalMillis : Double =
      if (streamCfg.exponential) {
        interval.toMillis * 2
      } else {
        interval.toMillis + streamCfg.minDelay.toMillis
      }

    newIntervalMillis = scala.math.min(
      streamCfg.maxDelay.toMillis,
      newIntervalMillis + newIntervalMillis * noise
    )

    newIntervalMillis.toLong.millis
  }

  def afterStreamStarted() : Unit = {}

  def starting(streamCfg :StreamControllerConfig, interval : FiniteDuration) : Receive = {
    case StreamController.Stop =>
      context.stop(self)

    case StreamController.Start =>
      log.debug(s"Initializing StreamController [${streamCfg.name}]")

      val (killswitch, done) = startStream()

      done.onComplete {
        case Success(_) =>
          self ! StreamController.StreamTerminated(None)
        case Failure(t) =>
          self ! StreamController.StreamTerminated(Some(t))
      }

      afterStreamStarted()
      context.become(running(streamCfg, killswitch, interval))
  }

  def beforeStreamRestart() : Unit = {}

  def running(streamCfg : StreamControllerConfig, killSwitch: KillSwitch, interval : FiniteDuration) : Receive = {
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

        beforeStreamRestart()
        context.system.scheduler.scheduleOnce(interval, self, StreamController.Start)

        context.become(starting(streamCfg, nextInterval(interval)(streamCfg)))
      } else {
        context.stop(self)
      }
  }

  def stopping : Receive = {
    case StreamController.StreamTerminated(_) => context.stop(self)
  }

  def startStream(): (KillSwitch, Future[Done]) = source()
    .viaMat(KillSwitches.single)(Keep.right)
    .watchTermination()(Keep.both)
    .toMat(Sink.ignore)(Keep.left)
    .run()

  def source() : Source[T,_]
}
