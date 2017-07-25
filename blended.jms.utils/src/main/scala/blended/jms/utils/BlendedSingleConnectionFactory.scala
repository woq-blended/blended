package blended.jms.utils

import java.lang.management.ManagementFactory
import javax.jms.{Connection, ConnectionFactory, JMSException}
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.util.Timeout
import blended.akka.OSGIActorConfig
import blended.jms.utils.internal._
import com.typesafe.config.Config
import org.osgi.framework.BundleContext
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._

trait IdAwareConnectionFactory extends ConnectionFactory {
  def clientId() : String
}

object BlendedSingleConnectionFactory {

  def apply(cfg: Config, vendor: String, provider: String, cf : ConnectionFactory, clientId: String)(implicit system: ActorSystem) =
    new BlendedSingleConnectionFactory(cfg, cf, vendor, provider, system, clientId, None, true)

  def apply(
    osgiCfg: OSGIActorConfig,
    cfCfg : Config,
    cf: ConnectionFactory,
    vendor : String,
    provider: String,
    enabled: Boolean
  )(implicit system: ActorSystem) : BlendedSingleConnectionFactory = {
    val config = BlendedJMSConnectionConfig(cfCfg)
    val clientId = osgiCfg.idSvc.resolvePropertyString(config.clientId)
    new BlendedSingleConnectionFactory(
      cfg = cfCfg,
      cf = cf,
      vendor = vendor,
      provider = provider,
      system = system,
      cId = clientId,
      bundleContext = Some(osgiCfg.bundleContext),
      enabled = enabled
    )
  }
}

class BlendedSingleConnectionFactory(
  cfg : Config,
  cf: ConnectionFactory,
  vendor: String,
  provider : String,
  system: ActorSystem,
  cId : String,
  bundleContext : Option[BundleContext],
  enabled : Boolean
) extends IdAwareConnectionFactory {

  private[this] implicit val eCtxt = system.dispatcher
  private[this] implicit val timeout = Timeout(100.millis)
  private[this] val log : Logger = LoggerFactory.getLogger(classOf[BlendedSingleConnectionFactory])

  private[this] val monitorName = s"Monitor-$vendor-$provider"
  private[this] val stateMgrName = s"JMS-$vendor-$provider"

  private[this] val config = BlendedJMSConnectionConfig(cfg)

  val holder = new ConnectionHolder(vendor, provider, cf, system)

  private[this] val actor =
    if (enabled) {

      val mbean : Option[ConnectionMonitor] = if (config.jmxEnabled) {
        val jmxServer = ManagementFactory.getPlatformMBeanServer()
        val jmxBean = new ConnectionMonitor(provider, cId)

        val objName = new ObjectName(s"blended:type=ConnectionMonitor,vendor=$vendor,provider=$provider")
        jmxServer.registerMBean(jmxBean, objName)

        Some(jmxBean)
      } else {
        None
      }

      val monitor = system.actorOf(ConnectionStateMonitor.props(bundleContext, mbean), monitorName)
      log.info(s"Connection State Monitor [$stateMgrName] created.")
      Some(system.actorOf(ConnectionStateManager.props(cfg, monitor, holder, cId), stateMgrName))
    } else {
      log.info(s"Connection State Monitor [$stateMgrName] is disabled by config setting.")
      None
    }

  actor.foreach { a => a ! CheckConnection(false) }

  @throws[JMSException]
  override def createConnection(): Connection = {

    if (enabled) {
      try {
        holder.getConnection() match {
          case Some(c) => c
          case None => throw new Exception(s"Error connecting to [$vendor:$provider].")
        }
      } catch {
        case e: Exception => {
          val jmsEx = new JMSException("Error getting Connection Factory")
          jmsEx.setLinkedException(e)
          throw jmsEx
        }
      }
    } else {
      throw new JMSException(s"Connection for provider [$vendor:$provider] is disabled.")
    }
  }

  override def createConnection(user: String, password: String): Connection = {
    log.warn("BlendedSingleConnectionFactory.createConnection() called with username and password, which is not supported.\nFalling back to default username and password.")
    createConnection()
  }

  override def clientId() : String = cId
}
