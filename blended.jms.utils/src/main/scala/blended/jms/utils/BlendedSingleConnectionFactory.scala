package blended.jms.utils

import javax.jms.{Connection, ConnectionFactory, JMSException}

import akka.actor.Props
import akka.util.Timeout
import blended.akka.OSGIActorConfig
import blended.jms.utils.internal.ConnectionControlActor
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._

object BlendedSingleConnectionFactory {

  private[this] val log = LoggerFactory.getLogger(classOf[BlendedSingleConnectionFactory])
  private[this] var connections : Map[String, Connection] = Map.empty

  def getConnection(provider : String) : Option[Connection] = connections.get(provider)

  def setConnection(provider : String, conn : Option[Connection]) : Unit = {
    conn match {
      case None =>
        log.info(s"Removing connection for provider [${provider}] from cache.")
        connections = connections.filterKeys( k => !k.equals(provider) )
      case Some(c) =>
        log.info(s"Caching connection for provider [${provider}].")
        connections = connections + (provider -> c)
    }
  }
}

class BlendedSingleConnectionFactory(
  cfg : OSGIActorConfig,
  cf: ConnectionFactory,
  provider : String,
  config : BlendedJMSConnectionConfig
) extends ConnectionFactory {

  private[this] implicit val eCtxt = cfg.system.dispatcher
  private[this] implicit val timeout = Timeout(100.millis)
  private[this] val log : Logger = LoggerFactory.getLogger(classOf[BlendedSingleConnectionFactory])

  private[this] val con = s"JMS-$provider"

  log.debug(s"Creating ConnectionControlActor [${con}]")

  private[this] val actor =
    if (config.enabled) {
      log.debug(s"ConnectionControlActor [${con}] created.")
      Some(cfg.system.actorOf(Props(ConnectionControlActor(provider, cf, config, cfg.bundleContext)), con))
    }
    else
      None

  @throws[JMSException]
  override def createConnection(): Connection = {

    if (config.enabled) {
      try {
        BlendedSingleConnectionFactory.getConnection(provider) match {
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
