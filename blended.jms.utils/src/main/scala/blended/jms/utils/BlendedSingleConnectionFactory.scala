package blended.jms.utils

import java.lang.management.ManagementFactory

import javax.jms.{Connection, ConnectionFactory, JMSException}
import javax.management.{MBeanInfo, ObjectName}
import akka.actor.ActorSystem
import akka.util.Timeout
import blended.jms.utils.internal._
import org.osgi.framework.BundleContext

import scala.concurrent.duration._
import blended.util.logging.Logger
import blended.jms.utils.internal.CheckConnection

trait IdAwareConnectionFactory extends ConnectionFactory with ProviderAware {
  val clientId : String

  override def id : String = super.id + s"($clientId)"
}

class BlendedSingleConnectionFactory(
  config : BlendedJMSConnectionConfig,
  system: ActorSystem,
  bundleContext : Option[BundleContext]
) extends IdAwareConnectionFactory {

  override val vendor = config.vendor
  override val provider = config.provider

  private[this] implicit val eCtxt = system.dispatcher
  private[this] implicit val timeout = Timeout(100.millis)
  private[this] val log : Logger = Logger[BlendedSingleConnectionFactory]

  private[this] val monitorName = s"Monitor-$vendor-$provider"
  private[this] val stateMgrName = s"JMS-$vendor-$provider"

  override val clientId : String = config.clientId

  val holder = new BlendedConnectionHolder(
    config = config,
    system = system
  )

  private[this] lazy val cfEnabled : Boolean = config.enabled && config.cfEnabled.forall(f => f(config))

  private[this] val actor =
    if (cfEnabled) {

      val mbean : Option[ConnectionMonitor] = if (config.jmxEnabled) {
        val jmxServer = ManagementFactory.getPlatformMBeanServer
        val jmxBean = new ConnectionMonitor(vendor, provider, clientId)

        val objName = new ObjectName(s"blended:type=ConnectionMonitor,vendor=$vendor,provider=$provider")

        if (jmxServer.isRegistered(objName)) {
          try {
            jmxServer.unregisterMBean(objName)
          } catch {
            case t : Throwable =>
          }
        }

        try {
          jmxServer.registerMBean(jmxBean, objName)
        } catch {
          case t : Throwable => log.warn(s"Could not register MBean [${objName.toString}]")
        }

        Some(jmxBean)
      } else {
        None
      }

      val monitor = system.actorOf(ConnectionStateMonitor.props(bundleContext, mbean), monitorName)
      log.info(s"Connection State Monitor [$stateMgrName] created.")
      Some(system.actorOf(ConnectionStateManager.props(config, monitor, holder), stateMgrName))
    } else {
      log.info(s"Connection State Monitor [$stateMgrName] is disabled by config setting.")
      None
    }

  actor.foreach { a => a ! CheckConnection(false) }

  @throws[JMSException]
  override def createConnection(): Connection = {

    if (cfEnabled) {
      try {
        holder.getConnection() match {
          case Some(c) => c
          case None => throw new Exception(s"Error connecting to [$id].")
        }
      } catch {
        case e: Exception => {
          val msg = s"Error getting Connection Factory [${e.getMessage()}]"
          log.error(msg)
          val jmsEx = new JMSException(msg)
          jmsEx.setLinkedException(e)
          throw jmsEx
        }
      }
    } else {
      throw new JMSException(s"Connection for provider [$id] is disabled.")
    }
  }

  override def createConnection(user: String, password: String): Connection = {
    log.warn(s"BlendedSingleConnectionFactory.createConnection() for [$id]called with username and password, which is not supported.\nFalling back to default username and password.")
    createConnection()
  }
}
