package blended.jms.utils.internal

import akka.actor.{Actor, ActorLogging, Props}
import blended.mgmt.base.FrameworkService
import domino.service_consuming.ServiceConsuming
import org.osgi.framework.BundleContext

object ConnectionStateMonitor {
  def props(bc : BundleContext) : Props = Props(new ConnectionStateMonitor(bc))
}

class ConnectionStateMonitor(override val bundleContext: BundleContext) extends Actor with ActorLogging with ServiceConsuming {

  override def receive: Receive = {
    case RestartContainer(t) =>
      restartContainer(t.getMessage())
      context.stop(self)
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
