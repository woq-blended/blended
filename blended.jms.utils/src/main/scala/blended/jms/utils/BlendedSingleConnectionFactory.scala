package blended.jms.utils

import java.lang.management.ManagementFactory
import javax.jms.{Connection, ConnectionFactory, JMSException}
import javax.management.ObjectName

import akka.util.Timeout
import blended.akka.OSGIActorConfig
import blended.jms.utils.internal._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._

class BlendedSingleConnectionFactory(
  cfg : OSGIActorConfig,
  cf: ConnectionFactory,
  provider : String,
  config : BlendedJMSConnectionConfig
) extends ConnectionFactory {

  private[this] implicit val eCtxt = cfg.system.dispatcher
  private[this] implicit val timeout = Timeout(100.millis)
  private[this] val log : Logger = LoggerFactory.getLogger(classOf[BlendedSingleConnectionFactory])

  private[this] val monitorName = s"Monitor-$provider"
  private[this] val stateMgrName = s"JMS-$provider"

  val holder = new ConnectionHolder(provider, cf)

  private[this] val actor =
    if (config.enabled) {

      val jmxServer = ManagementFactory.getPlatformMBeanServer()
      val mBean = new ConnectionMonitor(provider)

      val objName = new ObjectName(s"blended:type=ConnectionMonitor,provider=$provider")
      jmxServer.registerMBean(mBean, objName)

      val monitor = cfg.system.actorOf(ConnectionStateMonitor.props(cfg.bundleContext, mBean), monitorName)
      log.info(s"Connection State Monitor [$stateMgrName] created.")
      Some(cfg.system.actorOf(ConnectionStateManager.props(monitor, holder, config), stateMgrName))
    } else {
      log.info(s"Connection State Monitor [$stateMgrName] is disabled by config setting.")
      None
    }

  actor.foreach { a => a ! CheckConnection(false) }

  @throws[JMSException]
  override def createConnection(): Connection = {

    if (config.enabled) {
      try {
        holder.getConnection() match {
          case Some(c) => c
          case None => throw new Exception(s"Error connecting to $provider.")
        }
      } catch {
        case e: Exception => {
          val jmsEx = new JMSException("Error getting Connection Factory")
          jmsEx.setLinkedException(e)
          throw jmsEx
        }
      }
    } else {
      throw new JMSException(s"Connection for provider [$provider] is disabled.")
    }
  }

  override def createConnection(user: String, password: String): Connection = {
    log.warn("BlendedSingleConnectionFactory.createConnection() called with username and password, which is not supported.\nFalling back to default username and password.")
    createConnection()
  }
}
