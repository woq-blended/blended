package blended.jms.utils

import javax.jms.ConnectionFactory
import javax.naming.spi.InitialContextFactory

import blended.akka.ActorSystemWatching
import domino.DominoActivator
import org.osgi.framework.BundleContext
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

object ConnectionFactoryFactory {

  type CFCreator = (BlendedJMSConnectionConfig, Option[BundleContext]) => ConnectionFactory
  type CFEnabled = (BlendedJMSConnectionConfig, Option[BundleContext]) => Boolean

  val CONNECTION_URLS = "connectionURLs"
  val DEFAULT_USER = "defaultUser"
  val DEFAULT_PWD = "defaultPassword"
  val USE_JNDI = "useJndi"
  val CF_JNDI_NAME = "jndiName"
}

abstract class ConnectionFactoryFactory[T >: ConnectionFactory, S >: InitialContextFactory](
  implicit cfTag : ClassTag[T], ctxtTag : ClassTag[S]
) extends DominoActivator with ActorSystemWatching {

  import ConnectionFactoryFactory._

  val createConnectionFactory : Option[CFCreator] = None
  val connectionFactoryEnabled : Option[CFEnabled] = None

  private[this] lazy val loader = getClass().getClassLoader()
  private[this] lazy val cfClass = cfTag.runtimeClass.getName
  private[this] lazy val ctxtClass = ctxtTag.runtimeClass.getName

  private[this] val log : Logger = LoggerFactory.getLogger(getClass().getName())

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>

      val cfMap = osgiCfg.config.getObject("factories")

      cfMap.entrySet().asScala.foreach { entry =>

        val cfVendor = osgiCfg.config.getString("vendor")
        val cfProvider = entry.getKey()

        val cfCfg = BlendedJMSConnectionConfig(cfVendor, osgiCfg.config.getConfig("factories").getConfig(cfProvider)).copy(
          cfEnabled = connectionFactoryEnabled,
          cfCreator = createConnectionFactory,
          cfClassName = Some(cfClass),
          ctxtClassName = Some(ctxtClass),
          jmsClassloader = Some(Thread.currentThread().getContextClassLoader())
        )

        log.info(s"Configuring connection factory for vendor [$cfVendor] with provider [$cfProvider]")

        val singleCf = new BlendedSingleConnectionFactory(
          config = cfCfg,
          system = osgiCfg.system,
          bundleContext = Some(osgiCfg.bundleContext)
        )

        singleCf.providesService[ConnectionFactory, IdAwareConnectionFactory](
          "vendor" -> cfVendor,
          "provider" -> cfProvider
        )
      }
    }
  }

}
