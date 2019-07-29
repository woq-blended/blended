package blended.activemq.brokerstarter.internal

import java.lang.management.ManagementFactory
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, PoisonPill, Props}
import blended.akka.OSGIActorConfig
import blended.jms.utils.{BlendedSingleConnectionFactory, ConnectionConfig, IdAwareConnectionFactory}
import blended.util.logging.Logger
import domino.capsule.{CapsuleContext, SimpleDynamicCapsuleContext}
import domino.service_providing.ServiceProviding
import javax.jms.ConnectionFactory
import javax.net.ssl.SSLContext
import org.apache.activemq.broker.{BrokerFactory, BrokerService, DefaultBrokerFactory}
import org.apache.activemq.xbean.XBeanBrokerFactory
import org.osgi.framework.{BundleContext, ServiceRegistration}

import scala.util.control.NonFatal

object BrokerControlSupervisor {

  def props(
    cfg : OSGIActorConfig,
    sslContext : Option[SSLContext],
    broker : List[BrokerConfig]
  ) : Props = Props(new BrokerControlSupervisor(
    cfg, sslContext, broker
  ))
}

class BrokerControlSupervisor(
  cfg : OSGIActorConfig,
  sslContext : Option[SSLContext],
  broker : List[BrokerConfig]
) extends Actor {

  private[this] val log = Logger[BrokerControlSupervisor]

  private case object Start
  private case object Stop

  override def preStart() : Unit = {
    self ! Start
  }

  override def receive : Receive = {
    case Start =>

      log.info(s"Starting ${getClass().getSimpleName()} with [${broker.mkString(",")}]")
      broker.map { brokerCfg =>

        log.debug(s"Configuring Broker controller for [$brokerCfg]")
        val controlProps = BrokerControlActor.props(brokerCfg, cfg, sslContext)
        val actor = context.system.actorOf(controlProps, brokerCfg.brokerName)
        actor ! BrokerControlActor.StartBroker
      }

    case Stop =>
      context.children.foreach { a =>
        a ! BrokerControlActor.StopBroker
        a ! PoisonPill
      }
  }
}

object BrokerControlActor {

  case object StartBroker
  case object StopBroker
  case class BrokerStarted(uuid : String)

  val debugCnt : AtomicLong = new AtomicLong(0L)

  def props(
    brokerCfg : BrokerConfig,
    cfg : OSGIActorConfig,
    sslCtxt : Option[SSLContext]
  ) : Props = Props(new BrokerControlActor(brokerCfg, cfg, sslCtxt))
}

class BrokerControlActor(brokerCfg : BrokerConfig, cfg : OSGIActorConfig, sslCtxt : Option[SSLContext])
  extends Actor {

  private[this] val log = Logger[BrokerControlActor]
  private[this] var broker : Option[BrokerService] = None
  private[this] var svcReg : Option[ServiceRegistration[_]] = None
  private[this] var cfgReg : Option[ServiceRegistration[_]] = None
  private[this] val uuid = UUID.randomUUID().toString()

  override def toString : String = s"BrokerControlActor(${brokerCfg})"

  private[this] def startBroker() : Unit = {

    val oldLoader = Thread.currentThread().getContextClassLoader()

    val cfgDir = cfg.idSvc.containerContext.getProfileConfigDirectory()
    val uri = s"file://$cfgDir/${brokerCfg.file}"

    try {
      log.info(s"Starting ActiveMQ broker [${brokerCfg.brokerName}] with config file [$uri] ")

      Thread.currentThread().setContextClassLoader(classOf[DefaultBrokerFactory].getClassLoader())

      BrokerFactory.setStartDefault(false)
      val brokerFactory = new XBeanBrokerFactory()

      val b = brokerFactory.createBroker(new URI(uri))
      broker = Some(b)

      sslCtxt.foreach { ctxt =>
        val amqSslContext = new org.apache.activemq.broker.SslContext()
        amqSslContext.setSSLContext(ctxt)
        b.setSslContext(amqSslContext)
      }

      val actor = context.self

      // TODO: set Datadirectories from Code ??
      b.setBrokerName(brokerCfg.brokerName)
      b.setStartAsync(false)
      b.setSchedulerSupport(true)
      b.start()
      b.waitUntilStarted()
      log.info(s"ActiveMQ broker [${brokerCfg.brokerName}] started successfully.")
      actor ! BrokerControlActor.BrokerStarted(uuid)

    } catch {
      case NonFatal(t) =>
        log.warn(t)(s"Error starting ActiveMQ broker [${brokerCfg.brokerName}]")
        throw t
    } finally {
      Thread.currentThread().setContextClassLoader(oldLoader)
    }
  }

  private[this] def registerService(brokerCfg : BrokerConfig) : Unit = {
    if (svcReg.isEmpty) {
      new Object with ServiceProviding {

        override protected def capsuleContext : CapsuleContext = new SimpleDynamicCapsuleContext()

        override protected def bundleContext : BundleContext = cfg.bundleContext

        val url = s"vm://${brokerCfg.brokerName}?create=false"

        val jmsCfg = brokerCfg.copy(properties = brokerCfg.properties + ("brokerURL" -> url))

        val cf = new BlendedSingleConnectionFactory(
          config = jmsCfg,
          bundleContext = Some(cfg.bundleContext)
        )(system = cfg.system)

        svcReg = Some(cf.providesService[ConnectionFactory, IdAwareConnectionFactory](Map(
          "vendor" -> brokerCfg.vendor,
          "provider" -> brokerCfg.provider,
          "brokerName" -> brokerCfg.brokerName
        )))

        cfgReg = Some(brokerCfg.providesService[ConnectionConfig](Map(
          "vendor" -> brokerCfg.vendor,
          "provider" -> brokerCfg.provider,
          "brokerName" -> brokerCfg.brokerName
        )))
      }
    }
  }

  private[this] def stopBroker() : Unit = {

    broker.foreach { b =>
      log.info(s"Stopping ActiveMQ Broker [${brokerCfg.brokerName}]")
      try {
        b.stop()
        b.waitUntilStopped()
      } catch {
        case t : Throwable =>
          log.error(t)(s"Error stopping ActiveMQ broker [${brokerCfg.brokerName}]")
      } finally {
        try {
          log.info(s"Removing OSGi service for Activemq Broker [${brokerCfg.brokerName}]")
          svcReg.foreach(_.unregister())
          cfgReg.foreach(_.unregister())
        } catch {
          case _ : IllegalStateException => // was already unregistered
        }
      }
    }

    broker = None
    svcReg = None
  }

  override def preRestart(reason : Throwable, message : Option[Any]) : Unit = {
    log.error(reason)(s"Error starting Active MQ broker [${brokerCfg.brokerName}]")
    super.preRestart(reason, message)
  }

  override def postStop() : Unit = broker.foreach { b =>
    b.stop()
    b.waitUntilStopped()
  }

  private val jvmId = ManagementFactory.getRuntimeMXBean().getName()

  override def receive : Receive = {
    case BrokerControlActor.StartBroker =>
      log.trace(s"Received StartBroker Command for [$brokerCfg] [$jvmId][$uuid-${BrokerControlActor.debugCnt.incrementAndGet()}]")
      if (broker.isEmpty) { startBroker() }
    case started : BrokerControlActor.BrokerStarted =>
      log.trace(s"Received BrokerStarted Event for [$brokerCfg] [$jvmId][$uuid-${BrokerControlActor.debugCnt.incrementAndGet()}]")
      if (started.uuid == uuid) {
        broker.foreach { _ => registerService(brokerCfg) }
      }
    case BrokerControlActor.StopBroker =>
      log.trace(s"Received StopBroker Command for [$brokerCfg] [$jvmId][$uuid-${BrokerControlActor.debugCnt.incrementAndGet()}]")
      stopBroker()
  }
}
