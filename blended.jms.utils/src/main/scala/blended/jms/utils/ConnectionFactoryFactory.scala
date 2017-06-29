package blended.jms.utils

import javax.jms.ConnectionFactory

import blended.akka.{ActorSystemWatching, OSGIActorConfig}
import blended.container.context.ContainerPropertyResolver
import blended.util.ReflectionHelper
import com.typesafe.config.Config
import domino.DominoActivator
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

object ConnectionFactoryFactory {
  val CONNECTION_URLS = "connectionURLs"
  val DEFAULT_USER = "defaultUser"
  val DEFAULT_PWD = "defaultPassword"
}

abstract class ConnectionFactoryFactory extends DominoActivator with ActorSystemWatching {

  def createConnectionFactory(provider: String, osgiCfg: OSGIActorConfig, cfg: Config) : ConnectionFactory

  def vendor(osgiActorConfig: OSGIActorConfig) : String = osgiActorConfig.config.getString("vendor")

  def isEnabled(provider: String, osgiCfg: OSGIActorConfig, cfg: Config) : Boolean =
    !cfg.hasPath("enabled") || cfg.getBoolean("enabled")

  private[this] val log : Logger = LoggerFactory.getLogger(classOf[ConnectionFactoryFactory])

  protected def configureConnectionFactory(cf: ConnectionFactory, osgiActorCfg: OSGIActorConfig, cfg: Config) : Unit = {

    log.info(s"Configuring connection factory of type [${cf.getClass().getName()}].")
    val symbolicName = bundleContext.getBundle().getSymbolicName

    if (cfg.hasPath("properties")) {
      val propCfg = cfg.getObject("properties")

      propCfg.entrySet().asScala.foreach { entry =>

        val key = entry.getKey
        val value = ContainerPropertyResolver.resolve(osgiActorCfg.idSvc, cfg.getConfig("properties").getString(key))

        log.info(s"Setting property [$key] for connection factory [$symbolicName] to [$value].")
        ReflectionHelper.setProperty(cf, value, key)
      }
    }
  }

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>

      val cfMap = osgiCfg.config.getObject("factories")

      cfMap.entrySet().asScala.foreach { entry =>

        val provider = entry.getKey()
        log.info(s"Configuring connection factory for vendor [${vendor(osgiCfg)}] with provider [$provider]")

        val cfCfg = osgiCfg.config.getConfig("factories").getConfig(provider)

        val cf = createConnectionFactory(provider, osgiCfg, cfCfg)
        configureConnectionFactory(cf, osgiCfg, cfCfg)

        val singleCf = BlendedSingleConnectionFactory(
          osgiCfg = osgiCfg,
          cfCfg = cfCfg,
          cf = cf,
          vendor = vendor(osgiCfg),
          provider = provider,
          enabled = isEnabled(provider, osgiCfg, cfCfg)
        )(osgiCfg.system)
        singleCf.providesService[ConnectionFactory, IdAwareConnectionFactory](
          "vendor" -> vendor(osgiCfg),
          "provider" -> provider
        )

      }
    }
  }

}
