package blended.jms.utils

import javax.jms.ConnectionFactory

import blended.akka.{ActorSystemWatching, OSGIActorConfig}
import domino.DominoActivator
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

object ConnectionFactoryActivator {

  type CFEnabled = OSGIActorConfig => BlendedJMSConnectionConfig => Boolean

  val CONNECTION_URLS = "connectionURLs"
  val DEFAULT_USER = "defaultUser"
  val DEFAULT_PWD = "defaultPassword"
  val USE_JNDI = "useJndi"
  val CF_JNDI_NAME = "jndiName"
}

abstract class ConnectionFactoryActivator extends DominoActivator with ActorSystemWatching {

  import ConnectionFactoryActivator._

  private[this] lazy val loader = getClass().getClassLoader()

  val connectionFactoryEnabled : Option[CFEnabled] = None
  protected val cfClass : Option[String] = None
  protected val ctxtClass : Option[String] = None

  private[this] val log : Logger = LoggerFactory.getLogger(getClass().getName())

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>

      val cfMap = osgiCfg.config.getObject("factories")

      cfMap.entrySet().asScala.foreach { entry =>

        val cfVendor = osgiCfg.config.getString("vendor")
        val cfProvider = entry.getKey()

        val cfCfg = BlendedJMSConnectionConfig(cfVendor, osgiCfg.config.getConfig("factories").getConfig(cfProvider)).copy(
          cfEnabled = connectionFactoryEnabled.map(f => f(osgiCfg)),
          cfClassName = cfClass,
          ctxtClassName = ctxtClass,
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
