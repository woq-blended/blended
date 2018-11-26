package blended.mgmt.agent.internal

import scala.util.{ Failure, Try }

import akka.actor.Props
import blended.akka.{ OSGIActor, OSGIActorConfig }
import blended.updater.config.ContainerInfo
import blended.util.logging.Logger

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

  private[this] val log = Logger[OsgiMgmtReporter]

  val config: Try[MgmtReporterConfig] = MgmtReporterConfig.fromConfig(cfg.config) match {
    case f @ Failure(e) =>
      log.warn(e)("Incomplete management reporter config. Disabled connection to management server.")
      f
    case x =>
      log.info(s"Management reporter config: ${x}")
      x
  }

  private[this] val idSvc = cfg.idSvc

  protected def createContainerInfo: ContainerInfo =
    ContainerInfo(idSvc.uuid, idSvc.properties, serviceInfos.values.toList, profileInfo.profiles, System.currentTimeMillis(), Nil)

}

object OsgiMgmtReporter {

  def props(cfg: OSGIActorConfig): Props = Props(new OsgiMgmtReporter(cfg))
}
