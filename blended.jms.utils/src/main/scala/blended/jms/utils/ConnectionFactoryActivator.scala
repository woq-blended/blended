package blended.jms.utils

import java.io.{PrintWriter, StringWriter}
import javax.jms.ConnectionFactory

import blended.akka.{ActorSystemWatching, OSGIActorConfig}
import domino.DominoActivator
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

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

  protected val factoryClassLoader : Option[ClassLoader] = None

  private[this] val log : Logger = LoggerFactory.getLogger(getClass().getName())

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>

      try {
        log.info(s"Starting connection factory bundle [${osgiCfg.bundleContext.getBundle.getSymbolicName}]")

        val cfMap = osgiCfg.config.getObject("factories")

        cfMap.entrySet().asScala.foreach { entry =>

          val cfVendor = osgiCfg.config.getString("vendor")
          val cfProvider = entry.getKey()

          log.info(s"Configuring connection factory for vendor [$cfVendor] with provider [$cfProvider]")

          val fnEnabled = connectionFactoryEnabled.map(f => f(osgiCfg))

          val cfCfg = BlendedJMSConnectionConfig.fromConfig(osgiCfg.idSvc.resolvePropertyString)(
            vendor = cfVendor,
            provider = Some(cfProvider),
            cfg = osgiCfg.config.getConfig("factories").getConfig(cfProvider)
          )

          val enabled : Boolean = fnEnabled.map(f => f(cfCfg)).getOrElse(true)

          if (enabled) {
            val singleCf = new BlendedSingleConnectionFactory(
              config = cfCfg.copy(
                cfEnabled = fnEnabled,
                cfClassName = cfClass,
                ctxtClassName = ctxtClass,
                jmsClassloader = factoryClassLoader
              ),
              system = osgiCfg.system,
              bundleContext = Some(osgiCfg.bundleContext)
            )

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
