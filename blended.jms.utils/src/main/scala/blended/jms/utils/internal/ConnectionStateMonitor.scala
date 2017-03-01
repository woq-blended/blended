package blended.jms.utils.internal

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import blended.mgmt.base.FrameworkService
import domino.service_consuming.ServiceConsuming
import org.osgi.framework.BundleContext

object ConnectionStateMonitor {
  def props(bc : BundleContext, monitorBean: ConnectionMonitor) : Props = Props(new ConnectionStateMonitor(bc, monitorBean))
}

class ConnectionStateMonitor(override val bundleContext: BundleContext, val monitorBean: ConnectionMonitor)
  extends Actor with ActorLogging with ServiceConsuming {

  override def receive: Receive = LoggingReceive {
    case ConnectionStateChanged(state) =>
      val caller = sender()

      val response = state.copy(
        disconnectPending = monitorBean.getState().disconnectPending,
        connectPending = monitorBean.getState().connectPending
      )
      monitorBean.setState(state.copy(disconnectPending = false, connectPending = false))

      caller ! response

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
