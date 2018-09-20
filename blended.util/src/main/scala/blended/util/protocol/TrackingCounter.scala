package blended.util.protocol

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import blended.util.StatsCounter

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object TrackingCounter {
  def apply(idleTimeout: FiniteDuration = 3.seconds, counterFor: ActorRef): TrackingCounter =
    new TrackingCounter(idleTimeout, counterFor)
}

class TrackingCounter(idleTimeout: FiniteDuration, counterFor: ActorRef)
  extends Actor with ActorLogging {

  case object Tick

  implicit val ctxt : ExecutionContext = context.dispatcher
  implicit val timeout : Timeout = Timeout(3.seconds)

  var counter : ActorRef = _
  var timer   : Option[Cancellable] = None

  override def preStart(): Unit = {
    super.preStart()
    counter = context.actorOf(Props[StatsCounter])
    resetTimer()
  }

  override def receive: Receive = LoggingReceive {
    case Tick =>
      self ! StopCounter
    case ic : IncrementCounter =>
      resetTimer()
      counter.forward(ic)
    case StopCounter =>
      timer.foreach(_.cancel())
      (counter ? QueryCounter).mapTo[CounterInfo].map { info =>
        log.info(s"Tracking counter ending with [$info] for [$counterFor]")
        counterFor ! info
        context.stop(self)
      }
    case QueryCounter => counter.forward(QueryCounter)
  }

  private def resetTimer(): Unit = {
    timer.foreach(_.cancel())
    timer = Some(context.system.scheduler.scheduleOnce(idleTimeout, self, Tick))
  }
}
