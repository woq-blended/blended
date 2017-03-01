package blended.jms.utils.internal

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import blended.mgmt.base.FrameworkService
import domino.service_consuming.ServiceConsuming
import org.osgi.framework.BundleContext

import scala.concurrent.duration._

object ConnectionStateMonitor {
  def props(bc : BundleContext, monitorBean: ConnectionMonitor) : Props = Props(new ConnectionStateMonitor(bc, monitorBean))
}

class ConnectionStateMonitor(override val bundleContext: BundleContext, val monitorBean: ConnectionMonitor)
  extends Actor with ActorLogging with ServiceConsuming {

  private[this] implicit val eCtxt = context.system.dispatcher

  case object Tick

  override def preStart(): Unit = {
    super.preStart()
    context.system.scheduler.schedule(10.millis, 10.seconds, self, Tick)
  }

  override def receive: Receive = LoggingReceive {
    case ConnectionStateChanged(state) =>
      monitorBean.setState(state)

    case RestartContainer(t) =>
      restartContainer(t.getMessage())
      context.stop(self)

    case Tick =>
      context.system.eventStream.publish(monitorBean.getCommand())
      monitorBean.resetCommand()
  }

  private[this] def restartContainer(msg: String) : Unit = {
    log.warning(msg)

    withService[FrameworkService, Unit] { _ match {
      case None =>
        log.warning("Could not find FrameworkServive to restart Container. Restarting through Framework Bundle ...")
        bundleContext.getBundle(0).update()
      case Some(s) => s.restartContainer(msg, true)
    }}
  }

}
