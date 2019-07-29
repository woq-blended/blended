package blended.jms.utils.internal

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import blended.jms.utils.ConnectionState.RestartContainer
import blended.jms.utils.ConnectionStateChanged
import blended.mgmt.base.FrameworkService
import domino.service_consuming.ServiceConsuming
import org.osgi.framework.BundleContext

import scala.concurrent.duration._

object ConnectionStateMonitor {
  def props(
    vendor : String,
    provider : String,
    bc : Option[BundleContext],
    monitorBean : Option[ConnectionMonitor]
  ) : Props = Props(new ConnectionStateMonitor(vendor, provider,bc, monitorBean))
}

class ConnectionStateMonitor(
  val vendor : String,
  val provider : String,
  val bc : Option[BundleContext],
  val monitorBean : Option[ConnectionMonitor]
) extends Actor with ActorLogging with ServiceConsuming {

  override protected def bundleContext : BundleContext = bc match {
    case None       => throw new Exception("Bundle Context is not defined in this context")
    case Some(ctxt) => ctxt
  }

  private[this] implicit val eCtxt = context.system.dispatcher

  case object Tick

  override def preStart() : Unit = {
    super.preStart()
    self ! Tick
  }

  override def receive : Receive = LoggingReceive {

    case ConnectionStateChanged(state) =>
      if (state.vendor == vendor && state.provider == provider) {
        state.status match {
          case RestartContainer(t) =>
            restartContainer(t.getMessage())
            context.stop(self)
          case _ =>
            monitorBean.foreach{ mb => mb.setState(state) }
        }
      }

    case Tick =>
      monitorBean match {
        case Some(mb) =>
          context.system.eventStream.publish(mb.getCommand())
          mb.resetCommand()
        case None =>
      }
      context.system.scheduler.scheduleOnce(10.seconds, self, Tick)
  }

  private[this] def restartContainer(msg : String) : Unit = {
    log.warning(msg)

    withService[FrameworkService, Unit] {
      case None =>
        log.warning("Could not find FrameworkService to restart Container. Restarting through Framework Bundle ...")
        bundleContext.getBundle(0).update()
      case Some(s) => s.restartContainer(reason = msg, saveLogs = true)
    }
  }

}
