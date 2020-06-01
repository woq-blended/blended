package blended.jms.utils

import java.lang.management.ManagementFactory

import akka.actor.ActorSystem
import akka.util.Timeout
import blended.jms.utils.internal.{CheckConnection, _}
import blended.util.logging.Logger
import javax.jms.{Connection, ConnectionFactory, JMSException}
import javax.management.ObjectName
import org.osgi.framework.BundleContext

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait IdAwareConnectionFactory extends ConnectionFactory with ProviderAware {
  val clientId : String
  val stateMgr : Option[ActorRef]
  override def id : String = super.id + s"($clientId)"
}

class BlendedSingleConnectionFactory(
  val config : ConnectionConfig,
  bundleContext : Option[BundleContext]
)(implicit system : ActorSystem) extends IdAwareConnectionFactory {

  override val vendor : String = config.vendor
  override val provider : String = config.provider

  private[this] val log : Logger = Logger[BlendedSingleConnectionFactory]

  private[this] val monitorName = s"Monitor-$vendor-$provider"
  private[this] val stateMgrName = s"JMS-$vendor-$provider"

  override val clientId : String = config.clientId

  protected def createHolder(cfg : ConnectionConfig) : ConnectionHolder = if (config.useJndi) {
    new JndiConnectionHolder(cfg)(system)
  } else {
    new ReflectionConfigHolder(cfg)(system)
  }

  private val holder = createHolder(config)

  private[this] lazy val cfEnabled : Boolean = config.enabled && config.cfEnabled.forall(f => f(config))

  val stateMgr : Option[ActorRef] =
    if (cfEnabled) {

      val mbean : Option[ConnectionMonitor] = if (config.jmxEnabled) {
        val jmxServer = ManagementFactory.getPlatformMBeanServer
        val jmxBean = new ConnectionMonitor(vendor, provider, clientId)

        val objName = new ObjectName(s"blended:type=ConnectionMonitor,vendor=$vendor,provider=$provider")

        if (jmxServer.isRegistered(objName)) {
          try {
            jmxServer.unregisterMBean(objName)
          } catch {
            case NonFatal(t) =>
              log.warn(s"Failed to deregister MBean [${objName.toString()}]:[${t.getMessage()}]")
          }
        }

        try {
          jmxServer.registerMBean(jmxBean, objName)
        } catch {
          case NonFatal(t) =>
            log.warn(s"Could not register MBean [${objName.toString}]:[${t.getMessage()}]")
        }

        Some(jmxBean)
      } else {
        None
      }

      // Simply create the state monitor for that particular connection
      system.actorOf(ConnectionStateMonitor.props(vendor, provider, bundleContext, mbean), monitorName)
      log.info(s"Connection State Monitor [$stateMgrName] created.")
      Some(system.actorOf(ConnectionStateManager.props(config, holder), stateMgrName))
    } else {
      log.info(s"Connection State Monitor [$stateMgrName] is disabled by config setting.")
      None
    }

  stateMgr.foreach { a => a ! CheckConnection(false) }

  @throws[JMSException]
  override def createConnection() : Connection = {

    if (cfEnabled) {
      try {
        holder.getConnection() match {
          case Some(c) => c
          case None    => throw new Exception(s"Error connecting to [$id].")
        }
      } catch {
        case NonFatal(e) =>
          val msg = s"Error getting Connection Factory [${e.getMessage()}]"
          log.error(msg)
          val jmsEx : JMSException = new JMSException(msg)
          e match {
            case ex : Exception => jmsEx.setLinkedException(ex)
            case _ =>
          }
          throw jmsEx
      }
    } else {
      throw new JMSException(s"Connection for provider [$id] is disabled.")
    }
  }

  override def createConnection(user : String, password : String) : Connection = {
    log.warn(s"BlendedSingleConnectionFactory.createConnection() for [$id]called with username and password, " +
      "which is not supported.\nFalling back to default username and password.")
    createConnection()
  }
}

object SimpleIdAwareConnectionFactory {

  def apply(
    vendor : String,
    provider : String,
    clientId : String,
    cf : ConnectionFactory,
    minReconnect : FiniteDuration = 5.minutes
  )(implicit system : ActorSystem) : IdAwareConnectionFactory = {
    val cfg : ConnectionConfig = BlendedJMSConnectionConfig.defaultConfig.copy(
      vendor = vendor,
      provider = provider,
      clientId = clientId,
      keepAliveEnabled = false,
      minReconnect = minReconnect
    )

    new SimpleIdAwareConnectionFactory(cfg, cf, None)
  }
}

class SimpleIdAwareConnectionFactory(
  cfg : ConnectionConfig,
  cf : ConnectionFactory,
  bundleContext : Option[BundleContext]
)(implicit system : ActorSystem) extends BlendedSingleConnectionFactory(
  cfg, bundleContext
) {
  override def toString : String = s"${getClass().getSimpleName()}($vendor:$provider:$clientId)"
  override protected def createHolder(cfg : ConnectionConfig) : ConnectionHolder = new FactoryConfigHolder(cfg, cf)
}
