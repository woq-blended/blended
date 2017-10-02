package blended.jms.utils

import java.util
import javax.jms.{ConnectionFactory, JMSException}
import javax.naming.InitialContext

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
  val USE_JNDI = "useJndi"
  val CF_JNDI_NAME = "jndiName"
}

abstract class ConnectionFactoryFactory extends DominoActivator with ActorSystemWatching {

  import ConnectionFactoryFactory._

  def createConnectionFactory(provider: String, osgiCfg: OSGIActorConfig, cfg: Config) : ConnectionFactory =
    throw new JMSException("Not implemented")

  def vendor(osgiActorConfig: OSGIActorConfig) : String = osgiActorConfig.config.getString("vendor")

  def isEnabled(provider: String, osgiCfg: OSGIActorConfig, cfg: Config) : Boolean =
    !cfg.hasPath("enabled") || cfg.getBoolean("enabled")

  private[this] val log : Logger = LoggerFactory.getLogger(classOf[ConnectionFactoryFactory])

  protected def configureConnectionFactory(cf: ConnectionFactory, osgiActorCfg: OSGIActorConfig, cfg: Config) : ConnectionFactory = {

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

    cf
  }

  protected def lookupConnectionFactory(cfg : Config, name: String) : ConnectionFactory = {

    val envMap = new util.Hashtable[String, Object]()

    val cfgMap : Map[String, String] = cfg.getConfig("properties").entrySet().asScala.map{ e =>
      (e.getKey(), e.getValue().toString())
    }.toMap

    cfgMap.foreach{ case (k,v) => envMap.put(k, v) }

    try {
      log.info(s"Creating Initial context with properties [${cfgMap.mkString(", ")}]")
      val context = new InitialContext(envMap)
      log.info(s"Looking up JNDI name [$name]")
      context.lookup("foo").asInstanceOf[ConnectionFactory]
    } catch {
      case e : Exception =>
        log.warn(s"Could not lookup ConnectionFactory : [${e.getMessage()}]")
        val ex : JMSException = new JMSException("Could not lookup ConnectionFactory")
        ex.setLinkedException(e)
        throw ex
    }
  }

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>

      val cfMap = osgiCfg.config.getObject("factories")

      cfMap.entrySet().asScala.foreach { entry =>

        val cfVendor = vendor(osgiCfg)
        val cfProvider = entry.getKey()

        log.info(s"Configuring connection factory for vendor [$cfVendor] with provider [$cfProvider]")

        val cfCfg = osgiCfg.config.getConfig("factories").getConfig(cfProvider)

        val useJndi = if (cfCfg.hasPath(USE_JNDI)) cfCfg.getBoolean(USE_JNDI) else false
        val cfEnabled = isEnabled(cfProvider, osgiCfg, cfCfg)

        log.info(s"Connection factory for vendor [$cfVendor] uses JNDI [$useJndi], enabled [$cfEnabled]")

        val cf : ConnectionFactory = if (!useJndi) {
          configureConnectionFactory(
            createConnectionFactory(cfProvider, osgiCfg, cfCfg),
            osgiCfg, cfCfg
          )
        } else {
          lookupConnectionFactory(cfCfg, cfCfg.getString(CF_JNDI_NAME))
        }

        val singleCf = BlendedSingleConnectionFactory(
          osgiCfg = osgiCfg,
          cfCfg = cfCfg,
          cf = cf,
          vendor = vendor(osgiCfg),
          provider = cfProvider,
          enabled = cfEnabled
        )(osgiCfg.system)
        singleCf.providesService[ConnectionFactory, IdAwareConnectionFactory](
          "vendor" -> cfVendor,
          "provider" -> cfProvider
        )
      }
    }
  }

}
