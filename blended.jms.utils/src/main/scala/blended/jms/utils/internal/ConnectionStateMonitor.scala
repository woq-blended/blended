package blended.jms.utils.internal

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import blended.jms.utils.{ConnectionStateChanged, RestartContainer}
import blended.mgmt.base.FrameworkService
import domino.service_consuming.ServiceConsuming
import org.osgi.framework.BundleContext

import scala.concurrent.duration._

object ConnectionStateMonitor {
  def props(bc : Option[BundleContext], monitorBean : Option[ConnectionMonitor]) : Props = Props(new ConnectionStateMonitor(bc, monitorBean))
}

class ConnectionStateMonitor(val bc : Option[BundleContext], val monitorBean : Option[ConnectionMonitor])
  extends Actor with ActorLogging with ServiceConsuming {

  override protected def bundleContext : BundleContext = bc match {
    case None       => throw new Exception("Bundle Context is not defined in this context")
    case Some(ctxt) => ctxt
  }

  private[this] implicit val eCtxt = context.system.dispatcher

  case object Tick

  override def preStart() : Unit = {
    super.preStart()
    context.system.scheduler.schedule(10.millis, 10.seconds, self, Tick)
  }

  override def receive : Receive = LoggingReceive {
    case ConnectionStateChanged(state) => monitorBean match {
      case Some(mb) =>
        val oldState = mb.getState()
        if (oldState.status != state.status) {
          context.system.eventStream.publish(state)
        }
        mb.setState(state)
      case None =>
    }

    case RestartContainer(t) =>
      restartContainer(t.getMessage())
      context.stop(self)

    case Tick => monitorBean match {
      case Some(mb) =>
        context.system.eventStream.publish(mb.getCommand())
        mb.resetCommand()
      case None =>
    }
  }

  private[this] def restartContainer(msg : String) : Unit = {
    log.warning(msg)

    withService[FrameworkService, Unit] {
      case None =>
        log.warning("Could not find FrameworkService to restart Container. Restarting through Framework Bundle ...")
        bundleContext.getBundle(0).update()
      case Some(s) => s.restartContainer(msg, true)
    }
  }

}
