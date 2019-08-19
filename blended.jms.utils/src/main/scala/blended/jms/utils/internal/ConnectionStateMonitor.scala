package blended.jms.utils.internal

import akka.actor.{Actor, Props}
import blended.jms.utils.{ConnectionStateChanged, RestartContainer}
import blended.mgmt.base.FrameworkService
import blended.util.logging.Logger
import domino.service_consuming.ServiceConsuming
import org.osgi.framework.BundleContext

import scala.concurrent.ExecutionContext
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
) extends Actor with ServiceConsuming {

  private val log : Logger = Logger(s"${getClass().getName()}.$vendor.$provider")

  override protected def bundleContext : BundleContext = bc match {
    case None       => throw new Exception("Bundle Context is not defined in this context")
    case Some(ctxt) => ctxt
  }

  private[this] implicit val eCtxt : ExecutionContext = context.system.dispatcher

  case object Tick

  override def preStart() : Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[ConnectionStateChanged])
    self ! Tick
  }

  override def receive : Receive = {

    case ConnectionStateChanged(state) if state.vendor == vendor && state.provider == provider =>
      state.status match {
        case RestartContainer(t) =>
          restartContainer(t.getMessage())
          context.stop(self)
        case _ =>
          monitorBean.foreach{ mb =>
            log.debug(s"Updating state of JMX bean for [$vendor:$provider] with [$state]")
            mb.setState(state)
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
    log.warn(msg)

    withService[FrameworkService, Unit] {
      case None =>
        log.warn("Could not find FrameworkService to restart Container. Restarting through Framework Bundle ...")
        bundleContext.getBundle(0).update()
      case Some(s) => s.restartContainer(reason = msg, saveLogs = true)
    }
  }

}
