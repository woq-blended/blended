package blended.streams.dispatcher.internal.builder

import java.io.File

import akka.actor.ActorSystem
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.internal.BridgeActivator
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.internal.BlendedStreamsActivator
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
    idSvc : ContainerIdentifierService,
    system : ActorSystem,
    bs : DispatcherBuilderSupport
  )

  def country : String = "cc"
  def location : String = "09999"

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.streams" -> new BlendedStreamsActivator(),
    "blended.jms.bridge" -> new BridgeActivator()
  )

  def loggerName : String

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
        ctxt.bs.streamLogger.info(s"Successfully connected to [$cf]")
        Success(cf)
      case _ => Failure(new Exception(s"Unable to connect to [${cf.vendor}:${cf.provider}]"))
    }
  }

  def createDispatcherExecContext() : DispatcherExecContext = {
    val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](registry)(None)(
      clazz = ClassTag(classOf[ContainerIdentifierService]),
      timeout = 3.seconds
    )

    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)(
      clazz = ClassTag(classOf[ActorSystem]),
      timeout = 3.seconds
    )

    val provider : BridgeProviderRegistry = mandatoryService[BridgeProviderRegistry](registry)(None)(
      clazz = ClassTag(classOf[BridgeProviderRegistry]),
      timeout = 3.seconds
    )

    val cfg = ResourceTypeRouterConfig.create(
      idSvc,
      provider,
      idSvc.containerContext.getContainerConfig().getConfig("blended.streams.dispatcher")
    ).get

    val bs = new DispatcherBuilderSupport {
      override def containerConfig : Config = idSvc.getContainerContext().getContainerConfig()
      override val streamLogger : Logger = Logger(loggerName)
    }

    DispatcherExecContext(cfg = cfg, idSvc = idSvc, system = system, bs = bs)
  }

  def withDispatcherConfig[T](f : DispatcherExecContext => T) : T = f(createDispatcherExecContext())
}

