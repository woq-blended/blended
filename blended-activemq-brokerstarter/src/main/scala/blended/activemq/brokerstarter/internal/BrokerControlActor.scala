package blended.activemq.brokerstarter.internal

import java.net.URI
import javax.jms.ConnectionFactory

import akka.actor.{Actor, ActorLogging}
import blended.akka.OSGIActorConfig
import blended.jms.utils.{BlendedJMSConnectionConfig, BlendedSingleConnectionFactory}
import domino.capsule.{CapsuleContext, SimpleDynamicCapsuleContext}
import domino.service_providing.ServiceProviding
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.{BrokerFactory, BrokerService, DefaultBrokerFactory}
import org.apache.activemq.xbean.XBeanBrokerFactory
import org.osgi.framework.{BundleContext, ServiceRegistration}

import scala.language.reflectiveCalls

class BrokerControlActor extends Actor
  with ActorLogging {

  private[this] var cleanUp : List[() => Unit] = List.empty

  private[this] def startBroker(cfg: OSGIActorConfig) : (BrokerService, ServiceRegistration[ConnectionFactory]) = {

    val oldLoader = Thread.currentThread().getContextClassLoader()

    try {

      val cfgDir = cfg.idSvc.getContainerContext().getContainerConfigDirectory()

      val brokerName = cfg.config.getString("brokerName")
      val uri = s"file://$cfgDir/${cfg.config.getString("file")}"
      val provider = cfg.config.getString("provider")

      BrokerFactory.setStartDefault(false)

      Thread.currentThread().setContextClassLoader(classOf[DefaultBrokerFactory].getClassLoader())

      val brokerFactory = new XBeanBrokerFactory()
      brokerFactory.setValidate(false)

      val broker = brokerFactory.createBroker(new URI(uri))

      broker.setBrokerName(brokerName)
      broker.start()
      broker.waitUntilStarted()

      log.info(s"ActiveMQ broker [$brokerName] started successfully.")

      val registrar = new Object with ServiceProviding {

        override protected def capsuleContext: CapsuleContext = new SimpleDynamicCapsuleContext()
        override protected def bundleContext: BundleContext = cfg.bundleContext

        val url = s"vm://$brokerName?create=false"
        val amqCF : ConnectionFactory = new ActiveMQConnectionFactory(url)
        val cf : ConnectionFactory = new BlendedSingleConnectionFactory(cfg, amqCF, provider, BlendedJMSConnectionConfig.defaultConfig.copy(pingInterval = 60))

        val svcReg = cf.providesService[ConnectionFactory](Map(
          "provider" -> provider,
          "brokerName" -> brokerName
        ))
      }

      cleanUp = List(() => stopBroker(broker, registrar.svcReg))

      (broker, registrar.svcReg)
    } finally {
      Thread.currentThread().setContextClassLoader(oldLoader)
    }
  }

  private[this] def stopBroker(broker: BrokerService, svcReg: ServiceRegistration[ConnectionFactory]) : Unit = {
    log.info("Stopping ActiveMQ Broker [{}]", broker.getBrokerName())
    svcReg.unregister()

    broker.stop()
    broker.waitUntilStopped()

    cleanUp = List.empty
  }


  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error("Error starting Active MQ broker", reason)
    super.preRestart(reason, message)
  }

  override def postStop(): Unit = cleanUp.foreach(t => t())

  override def receive : Receive = withoutBroker

  def withoutBroker : Receive = {
    case StartBroker(cfg : OSGIActorConfig) =>
      val (broker, reg) = startBroker(cfg)
      context.become(withBroker(broker, reg))
    case StopBroker =>
      log.debug("Ignoring stop command for ActiveMQ as Broker is already stopped")
  }

  def withBroker(broker: BrokerService, reg: ServiceRegistration[ConnectionFactory]) : Receive = {
    case StartBroker =>
      log.debug("Ignoring start command for ActiveMQ as Broker is already started")
    case StopBroker =>
      stopBroker(broker, reg)
      context.become(withoutBroker)
  }
}
