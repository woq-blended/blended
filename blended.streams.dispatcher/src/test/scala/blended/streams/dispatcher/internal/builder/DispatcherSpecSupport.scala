package blended.streams.dispatcher.internal.builder

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.bridge.internal.BridgeActivator
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.internal.BlendedStreamsActivator
import blended.streams.message.FlowEnvelopeLogger
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{BlendedPojoRegistry, JmsConnectionHelper, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import com.typesafe.config.Config
import org.osgi.framework.BundleActivator
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.ExecutionContext

trait DispatcherSpecSupport extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with BeforeAndAfterAll
  with JmsConnectionHelper {

  case class DispatcherExecContext(
    cfg : ResourceTypeRouterConfig,
    ctCtxt : ContainerContext,
    system : ActorSystem,
    bs : DispatcherBuilderSupport,
    envLogger : FlowEnvelopeLogger
  ) {
    val materializer : Materializer = ActorMaterializer()(system)
    val execCtxt : ExecutionContext = system.dispatcher
  }

  def country : String = "cc"
  def location : String = "09999"

  private var _dispCtxt : Option[DispatcherExecContext] = None
  def dispCtxt : DispatcherExecContext = _dispCtxt match {
    case Some(c) => c
    case None => throw new Exception(s"Dispatcher exec context not yet defined.")
  }

  private var _cf : Option[IdAwareConnectionFactory] = None
  def cf : IdAwareConnectionFactory = _cf match {
    case Some(c) => c
    case None => throw new Exception(s"Connection factory  not yet defined.")
  }

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.streams" -> new BlendedStreamsActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.jms.bridge" -> new BridgeActivator()
  )

  def loggerName: String
  private val logger : Logger = Logger(loggerName)

  System.setProperty("AppCountry", country)
  System.setProperty("AppLocation", location)

  def providerId(vendor : String, provider : String) : String =
    classOf[BridgeProviderConfig].getSimpleName() + s"($vendor:$provider)"

  def createDispatcherExecContext(r : BlendedPojoRegistry) : DispatcherExecContext = {
    implicit val system : ActorSystem = mandatoryService[ActorSystem](r)
    val provider : BridgeProviderRegistry = mandatoryService[BridgeProviderRegistry](r)

    val cfg = ResourceTypeRouterConfig.create(
      ctCtxt,
      provider,
      ctCtxt.containerConfig.getConfig("blended.streams.dispatcher")
    ).get

    val bs = new DispatcherBuilderSupport {
      override def containerConfig : Config = ctCtxt.containerConfig
    }

    DispatcherExecContext(
      cfg = cfg,
      ctCtxt = ctCtxt,
      system = system,
      bs = bs,
      envLogger = FlowEnvelopeLogger.create(bs.headerConfig, logger)
    )
  }

  def withDispatcherConfig[T](r : BlendedPojoRegistry)(f : DispatcherExecContext => T) : T = f(createDispatcherExecContext(r))

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    mandatoryService[ActorSystem](registry)

    val dispCtxt = createDispatcherExecContext(registry)

    val (internalVendor, internalProvider) = dispCtxt.cfg.providerRegistry.internalProvider.map(p => (p.vendor, p.provider)).get

    _cf  = Some(namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)(vendor = internalVendor, provider = internalProvider).get)
    _dispCtxt = Some(dispCtxt)
  }
}

