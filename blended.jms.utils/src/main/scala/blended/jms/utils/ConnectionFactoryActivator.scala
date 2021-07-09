package blended.jms.utils

import java.io.{PrintWriter, StringWriter}

import blended.akka.{ActorSystemWatching, OSGIActorConfig}
import blended.util.logging.Logger
import domino.DominoActivator
import javax.jms.ConnectionFactory

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

object ConnectionFactoryActivator {

  type CFEnabled = OSGIActorConfig => ConnectionConfig => Boolean

  val CONNECTION_URLS = "connectionURLs"
  val DEFAULT_USER = "defaultUser"
  val DEFAULT_PWD = "defaultPassword"
  val USE_JNDI = "useJndi"
  val CF_JNDI_NAME = "jndiName"
}

abstract class ConnectionFactoryActivator extends DominoActivator with ActorSystemWatching {

  import ConnectionFactoryActivator._

  protected val connectionFactoryEnabled: Option[CFEnabled] = None

  protected val cfClass: Option[String] = None
  protected val ctxtClass: Option[String] = None

  protected val factoryClassLoader: Option[ClassLoader] = None

  private[this] val log: Logger = Logger(getClass().getName())

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>
      try {
        log.info(s"Starting connection factory bundle [${osgiCfg.bundleContext.getBundle.getSymbolicName}]")

        val cfMap = osgiCfg.config.getObject("factories")

        cfMap.entrySet().asScala.foreach { entry =>
          val cfVendor = osgiCfg.config.getString("vendor")
          val cfProvider = entry.getKey()

          log.info(s"Configuring connection factory for vendor [${cfVendor}:${cfProvider}]")

          val fnEnabled = connectionFactoryEnabled.map(f => f(osgiCfg))

          val cfCfg = BlendedJMSConnectionConfig.fromConfig(osgiCfg.ctContext)(
            vendor = cfVendor,
            provider = cfProvider,
            cfg = osgiCfg.config.getConfig("factories").getConfig(cfProvider)
          )

          val enabled: Boolean = fnEnabled.forall(_(cfCfg))

          if (enabled) {
            val singleCf = new BlendedSingleConnectionFactory(
              config = cfCfg.copy(
                cfEnabled = fnEnabled,
                ctxtClassName = ctxtClass,
                jmsClassloader = factoryClassLoader
              ),
              bundleContext = Some(osgiCfg.bundleContext)
            )(system = osgiCfg.system)

            singleCf.providesService[ConnectionFactory, IdAwareConnectionFactory](
              "vendor" -> cfVendor,
              "provider" -> cfProvider
            )
          } else {
            log.info(s"Connection factory [$cfVendor:$cfProvider] is disabled.")
          }
        }
      } catch {
        case NonFatal(t) =>
          val sw = new StringWriter()
          val writer = new PrintWriter(sw)
          t.printStackTrace(writer)

          log.warn("Error starting Connection Factory bundle...")
          log.error(sw.toString)
          throw t
      }
    }
  }

}
