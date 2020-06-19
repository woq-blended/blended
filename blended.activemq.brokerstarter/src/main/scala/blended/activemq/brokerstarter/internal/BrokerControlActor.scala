package blended.activemq.brokerstarter.internal

import java.io.File
import java.lang.management.ManagementFactory
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
import org.apache.activemq.broker.{BrokerFactory, BrokerPlugin, BrokerService, DefaultBrokerFactory}
import org.apache.activemq.xbean.XBeanBrokerFactory
import org.osgi.framework.{BundleContext, ServiceRegistration}
import org.springframework.core.io.{FileSystemResource, Resource}

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
  private[this] val uuid = UUID.randomUUID().toString()

  override def toString : String = s"BrokerControlActor($brokerCfg)"

  private[this] def startBroker() : Unit = {

    val oldLoader = Thread.currentThread().getContextClassLoader()


    try {
      val cfgDir = cfg.ctContext.profileConfigDirectory
      val f : File = new File(cfgDir, brokerCfg.file).getAbsoluteFile()
      assert(f.exists() && f.isFile() && f.canRead())

      val resource : Resource = new FileSystemResource(f)

      log.info(s"Loading ActiveMQ broker [${brokerCfg.brokerName}] with config file [${f.getAbsolutePath()}] ")

      Thread.currentThread().setContextClassLoader(classOf[DefaultBrokerFactory].getClassLoader())

      BrokerFactory.setStartDefault(false)
      val brokerFactory = new XBeanBrokerFactory()

      val b : BrokerService = brokerFactory.createBroker(resource.getURI())
      log.debug(s"Configuring ActiveMQ broker [${brokerCfg.brokerName}]")
      broker = Some(b)

      if (brokerCfg.withAuthentication) {
        log.info(s"The broker [${brokerCfg.brokerName}] will start with authentication")
        val plugins : List[BrokerPlugin] = new JaasAuthenticationPlugin(brokerCfg) :: Option(b.getPlugins()).map(_.toList).getOrElse(List.empty)
        b.setPlugins(plugins.toArray)
      } else {
        log.info(s"The broker [${brokerCfg.brokerName}] will start without authentication")
      }

      sslCtxt.foreach { ctxt =>
        val amqSslContext = new org.apache.activemq.broker.SslContext()
        amqSslContext.setSSLContext(ctxt)
        b.setSslContext(amqSslContext)
      }

      val actor = context.self

      // TODO: set Datadirectories from Code ??
      b.setBrokerName(brokerCfg.brokerName)
      log.info(s"About to start Active MQ broker [${brokerCfg.brokerName}]")
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

        val jmsCfg : ConnectionConfig = brokerCfg.copy(
          properties = brokerCfg.properties + ("brokerURL" -> url),
          jmsClassloader = Some(getClass().getClassLoader())
        )

        val cf = new BlendedSingleConnectionFactory(
          config = jmsCfg,
          bundleContext = Some(cfg.bundleContext)
        )(system = cfg.system)

        svcReg = Some(cf.providesService[ConnectionFactory, IdAwareConnectionFactory](Map(
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
        case NonFatal(t) =>
          log.error(t)(s"Error stopping ActiveMQ broker [${brokerCfg.brokerName}]")
      } finally {
        try {
          log.info(s"Removing OSGi service for Activemq Broker [${brokerCfg.brokerName}]")
          svcReg.foreach(_.unregister())
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

  private val jvmId : String = ManagementFactory.getRuntimeMXBean().getName()

  override def receive : Receive = {
    case BrokerControlActor.StartBroker =>
      log.trace(s"Received StartBroker Command for [$brokerCfg] [$jvmId][$uuid-${BrokerControlActor.debugCnt.incrementAndGet()}]")
      if (broker.isEmpty) { startBroker() }
    case started : BrokerControlActor.BrokerStarted =>
      if (started.uuid == uuid) {
        log.debug(s"Received BrokerStarted Event for [$brokerCfg] [$jvmId][$uuid-${BrokerControlActor.debugCnt.incrementAndGet()}]")
        broker.foreach { _ => registerService(brokerCfg) }
      }
    case BrokerControlActor.StopBroker =>
      log.trace(s"Received StopBroker Command for [$brokerCfg] [$jvmId][$uuid-${BrokerControlActor.debugCnt.incrementAndGet()}]")
      stopBroker()
  }
}
