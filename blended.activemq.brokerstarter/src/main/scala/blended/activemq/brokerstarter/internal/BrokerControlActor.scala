package blended.activemq.brokerstarter.internal

import java.net.URI

import akka.actor.{Actor, OneForOneStrategy, Props, SupervisorStrategy}
import akka.pattern.{Backoff, BackoffSupervisor}
import blended.akka.OSGIActorConfig
import blended.jms.utils.{BlendedJMSConnectionConfig, BlendedSingleConnectionFactory, IdAwareConnectionFactory}
import blended.util.logging.Logger
import domino.capsule.{CapsuleContext, SimpleDynamicCapsuleContext}
import domino.service_providing.ServiceProviding
import javax.jms.ConnectionFactory
import javax.net.ssl.SSLContext
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.{BrokerFactory, BrokerService, DefaultBrokerFactory}
import org.apache.activemq.xbean.XBeanBrokerFactory
import org.osgi.framework.{BundleContext, ServiceRegistration}

import scala.concurrent.duration._
import scala.language.reflectiveCalls
import scala.util.control.NonFatal

object BrokerControlSupervisor {

  def props(
    cfg: OSGIActorConfig,
    sslContext : Option[SSLContext],
    broker : List[BrokerConfig]
  ) : Props = Props(new BrokerControlSupervisor(
    cfg, sslContext, broker
  ))
}

class BrokerControlSupervisor(
  cfg : OSGIActorConfig,
  sslContext: Option[SSLContext],
  broker : List[BrokerConfig]
) extends Actor {

  private[this] val log = Logger[BrokerControlSupervisor]

  private case object Start

  override def preStart(): Unit = {
    self ! Start
  }

  override def receive: Receive = {
    case Start =>

      log.info(s"Starting ${getClass().getSimpleName()} with [${broker.mkString(",")}]")
      broker.map { brokerCfg =>

        log.debug(s"Configuring Broker controller for [$brokerCfg]")
        val controlProps = BrokerControlActor.props(brokerCfg, cfg, sslContext)
        val restartProps = BackoffSupervisor.props(
          Backoff.onStop(
            childProps = controlProps,
            childName = brokerCfg.brokerName,
            minBackoff = 10.seconds,
            maxBackoff = 2.minutes,
            randomFactor = 0.2,
            maxNrOfRetries = -1
          ).withAutoReset(10.seconds)
            .withSupervisorStrategy(
              OneForOneStrategy() {
                case _ : Throwable => SupervisorStrategy.Restart
              }
            )
        )


        context.system.actorOf(restartProps, brokerCfg.brokerName)
      }
  }
}

object BrokerControlActor {

  case object StartBroker
  case object StopBroker

  def props(
    brokerCfg : BrokerConfig,
    cfg : OSGIActorConfig,
    sslCtxt : Option[SSLContext]
  ) : Props = Props(new BrokerControlActor(brokerCfg, cfg, sslCtxt))
}

class BrokerControlActor(brokerCfg: BrokerConfig, cfg: OSGIActorConfig, sslCtxt: Option[SSLContext])
  extends Actor {

  private[this] val log = Logger[BrokerControlActor]


  override def preStart(): Unit = self ! BrokerControlActor.StartBroker

  // Memorize pending cleanup tasks
  private[this] var cleanUp : List[() => Unit] = List.empty

  private[this] def startBroker() :
    (BrokerService, ServiceRegistration[BlendedSingleConnectionFactory]) = {

    val oldLoader = Thread.currentThread().getContextClassLoader()

    val cfgDir = cfg.idSvc.containerContext.getProfileConfigDirectory()
    val uri = s"file://$cfgDir/${brokerCfg.file}"

    try {

      log.info(s"Starting ActiveMQ broker [${brokerCfg.brokerName}] with config file [$uri] ")

      BrokerFactory.setStartDefault(false)

      Thread.currentThread().setContextClassLoader(classOf[DefaultBrokerFactory].getClassLoader())

      val brokerFactory = new XBeanBrokerFactory()
      brokerFactory.setValidate(false)

      val broker = brokerFactory.createBroker(new URI(uri))
      sslCtxt.foreach{ ctxt =>
        val amqSslContext = new org.apache.activemq.broker.SslContext()
        amqSslContext.setSSLContext(ctxt)
        broker.setSslContext(amqSslContext)
      }

      // TODO: set Datadirectories from Code ?
      broker.setBrokerName(brokerCfg.brokerName)
      broker.start()
      broker.waitUntilStarted()

      log.info(s"ActiveMQ broker [${brokerCfg.brokerName}] started successfully.")

      val registrar = new Object with ServiceProviding {

        override protected def capsuleContext: CapsuleContext = new SimpleDynamicCapsuleContext()

        override protected def bundleContext: BundleContext = cfg.bundleContext

        val url = s"vm://${brokerCfg.brokerName}?create=false"

        val jmsCfg : BlendedJMSConnectionConfig = BlendedJMSConnectionConfig.fromConfig(cfg.idSvc.resolvePropertyString)(
          brokerCfg.vendor,
          provider = brokerCfg.provider,
          cfg = cfg.config.getConfig("broker").getConfig(brokerCfg.brokerName)
        )

        val props : Map[String,String] = jmsCfg.properties + ("brokerURL" -> url)

        val cf = new BlendedSingleConnectionFactory(
          jmsCfg.copy(
            properties = props,
            cfClassName = Some(classOf[ActiveMQConnectionFactory].getName),
            clientId = brokerCfg.clientId
          ),
          cfg.system,
          Some(cfg.bundleContext)
        )

        val svcReg : ServiceRegistration[BlendedSingleConnectionFactory] = cf.providesService[ConnectionFactory, IdAwareConnectionFactory](Map(
          "vendor" -> brokerCfg.vendor,
          "provider" -> brokerCfg.provider,
          "brokerName" -> brokerCfg.brokerName
        ))
      }

      cleanUp = List(() => stopBroker(broker, registrar.svcReg))

      (broker, registrar.svcReg)

    } catch {
      case NonFatal(t) =>
        log.warn(t)(s"Error starting ActiveMQ broker [${brokerCfg.brokerName}]")
        throw t
    } finally {
      Thread.currentThread().setContextClassLoader(oldLoader)
    }
  }

  private[this] def stopBroker(broker: BrokerService, svcReg: ServiceRegistration[BlendedSingleConnectionFactory]) : Unit = {
    log.info(s"Stopping ActiveMQ Broker [${brokerCfg.brokerName}]")
    
    try {
      broker.stop()
      broker.waitUntilStopped()
    } catch {
      case t : Throwable => 
        log.error(t)(s"Error stopping ActiveMQ broker [${brokerCfg.brokerName}]")
    } finally {
        try { 
          svcReg.unregister() 
        } catch {
          case _ : IllegalStateException => // was already unregistered
        }

      cleanUp = List.empty
    }
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error(reason)(s"Error starting Active MQ broker [${brokerCfg.brokerName}]")
    super.preRestart(reason, message)
  }

  override def postStop(): Unit = cleanUp.foreach(t => t())

  override def receive : Receive = withoutBroker

  def withoutBroker : Receive = {
    case BrokerControlActor.StartBroker =>
      val (broker, reg) = startBroker()
      context.become(withBroker(broker, reg))
    case BrokerControlActor.StopBroker =>
      log.debug("Ignoring stop command for ActiveMQ as Broker is already stopped")
  }

  def withBroker(broker: BrokerService, reg: ServiceRegistration[BlendedSingleConnectionFactory]) : Receive = {
    case BrokerControlActor.StartBroker =>
      log.debug("Ignoring start command for ActiveMQ as Broker is already started")
    case BrokerControlActor.StopBroker =>
      stopBroker(broker, reg)
      context.become(withoutBroker)
  }
}
