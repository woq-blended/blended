package blended.mgmt.agent.internal

import akka.actor.{ Cancellable, Props }
import akka.event.LoggingReceive
import akka.pattern.pipe
import blended.akka.{ OSGIActor, OSGIActorConfig }
import blended.spray.SprayPrickleSupport
import blended.updater.config.{ ContainerInfo, ContainerRegistryResponseOK, ServiceInfo }
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spray.client.pipelining._
import spray.http.HttpRequest

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Try }
import blended.updater.config.json.PrickleProtocol._
import blended.updater.config.Profile
import blended.updater.config.ProfileInfo

/**
 * Actor, that collects various container information and send's it to a remote management container.
 *
 * Sources of information:
 *
 * * [[ServiceInfo]] from the Akka event stream
 * * `([[Long]], List[[[Profile]]])` from the Akka event stream
 *
 * Send to remote container:
 *
 * * [[ContainerInfo]] send via HTTP POST request
 *
 * Configuration:
 *
 * This actor reads a configuration class [[MgmtReporterConfig]] from the [[OSGIActorConfig]].
 * Only if all necessary configuration are set (currently `initialUpdateDelayMsec` and `updateIntervalMsec`), the reporter sends information to the management container.
 * The target URL of the management container is configured with the `registryUrl` config entry.
 *
 */
class OsgiMgmtReporter(cfg: OSGIActorConfig) extends OSGIActor(cfg) with MgmtReporter {

  import MgmtReporter._

  private[this] val log = LoggerFactory.getLogger(classOf[MgmtReporter])

  val config: Try[MgmtReporterConfig] = MgmtReporterConfig.fromConfig(cfg.config) match {
    case f @ Failure(e) =>
      log.warn("Incomplete management reporter config. Disabled connection to management server.", e)
      f
    case x =>
      log.info("Management reporter config: {}", x)
      x
  }

  private[this] val idSvc = cfg.idSvc

  protected def createContainerInfo: ContainerInfo =
    ContainerInfo(idSvc.uuid, idSvc.properties, serviceInfos.values.toList, profileInfo.profiles, System.currentTimeMillis())

}

object OsgiMgmtReporter {

  def props(cfg: OSGIActorConfig): Props = Props(new OsgiMgmtReporter(cfg))
}
