package blended.streams.dispatcher.internal.builder

import java.io.File

import akka.actor.ActorSystem
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.bridge.internal.BridgeActivator
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.BlendedStreamsConfig
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.internal.BlendedStreamsActivator
import blended.streams.message.FlowEnvelopeLogger
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import com.typesafe.config.Config
import javax.jms.Connection
import org.osgi.framework.BundleActivator

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

trait DispatcherSpecSupport extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper {

  case class DispatcherExecContext(
    cfg : ResourceTypeRouterConfig,
    ctCtxt : ContainerContext,
    system : ActorSystem,
    bs : DispatcherBuilderSupport,
    envLogger : FlowEnvelopeLogger
  )

  def country : String = "cc"
  def location : String = "09999"

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.streams" -> new BlendedStreamsActivator(),
    "blended.jms.bridge" -> new BridgeActivator()
  )

  def loggerName: String
  private val logger : Logger = Logger(loggerName)

  System.setProperty("AppCountry", country)
  System.setProperty("AppLocation", location)

  def providerId(vendor : String, provider : String) : String =
    classOf[BridgeProviderConfig].getSimpleName() + s"($vendor:$provider)"

  def jmsConnectionFactory(sr : BlendedPojoRegistry, ctxt : DispatcherExecContext)(
    vendor : String, provider : String, timeout : FiniteDuration
  ) : Try[IdAwareConnectionFactory] = {

    implicit val to : FiniteDuration = timeout
    val started = System.currentTimeMillis()

    val cf = mandatoryService[IdAwareConnectionFactory](sr)(Some(s"(&(vendor=$vendor)(provider=$provider))"))
    var con : Option[Connection] = None

    do {
      // scalastyle:off magic.number
      Thread.sleep(100)
      // scalastyle:on magic.number
      con = Try {
        cf.createConnection()
      } match {
        case Success(c) => Some(c)
        case Failure(t) => None
      }
    } while (con.isEmpty && System.currentTimeMillis() - started <= timeout.toMillis)

    con match {
      case Some(_) =>
        logger.info(s"Successfully connected to [$cf]")
        Success(cf)
      case _ => Failure(new Exception(s"Unable to connect to [${cf.vendor}:${cf.provider}]"))
    }
  }

  def createDispatcherExecContext() : DispatcherExecContext = {
    val ctCtxt : ContainerContext = mandatoryService[ContainerContext](registry)(None)(
      clazz = ClassTag(classOf[ContainerContext]),
      timeout = 3.seconds
    )

    val streamsCfg : BlendedStreamsConfig = BlendedStreamsConfig.create(ctCtxt)

    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)(
      clazz = ClassTag(classOf[ActorSystem]),
      timeout = 3.seconds
    )

    val provider : BridgeProviderRegistry = mandatoryService[BridgeProviderRegistry](registry)(None)(
      clazz = ClassTag(classOf[BridgeProviderRegistry]),
      timeout = 3.seconds
    )

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

  def withDispatcherConfig[T](f : DispatcherExecContext => T) : T = f(createDispatcherExecContext())
}

