package blended.mgmt.agent.internal

import akka.actor.{Cancellable, Props}
import akka.event.LoggingReceive
import akka.pattern.pipe
import blended.akka.{OSGIActor, OSGIActorConfig}
import blended.spray.SprayUPickleSupport
import blended.updater.config.{ContainerInfo, ContainerRegistryResponseOK, ServiceInfo}
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spray.client.pipelining._
import spray.http.HttpRequest

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Try}

object MgmtReporter {

  object MgmtReporterConfig {
    def fromConfig(config: Config): Try[MgmtReporterConfig] = Try {
      MgmtReporterConfig(
        registryUrl = config.getString("registryUrl"),
        updateIntervalMsec = if (config.hasPath("updateIntervalMsec")) config.getLong("updateIntervalMsec") else 0,
        initialUpdateDelayMsec = if (config.hasPath("initialUpdateDelayMsec")) config.getLong("initialUpdateDelayMsec") else 0
      )
    }
  }

  case class MgmtReporterConfig(
      registryUrl: String,
      updateIntervalMsec: Long,
      initialUpdateDelayMsec: Long) {

    override def toString(): String = s"${getClass().getSimpleName()}(registryUrl=${registryUrl},updateInetervalMsec=${updateIntervalMsec},initialUpdateDelayMsec=${initialUpdateDelayMsec})"
  }

  case object Tick

  def props(cfg: OSGIActorConfig): Props = Props(new MgmtReporter(cfg))
}

class MgmtReporter(cfg: OSGIActorConfig) extends OSGIActor(cfg) with SprayUPickleSupport {

  import upickle.default._
  import MgmtReporter._

  ////////////////////
  // MUTABLE
  private[this] var ticker: Option[Cancellable] = None
  private[this] var serviceInfos: Map[String, ServiceInfo] = Map()
  ////////////////////

  private[this] val log = LoggerFactory.getLogger(classOf[MgmtReporter])

  implicit private[this] val eCtxt = context.system.dispatcher
  private[this] val idSvc = cfg.idSvc
  private[this] val config: Try[MgmtReporterConfig] = MgmtReporterConfig.fromConfig(cfg.config) match {
    case f @ Failure(e) =>
      log.warn("Incomplete management reporter config. Disabled connection to management server.", e)
      f
    case x =>
      log.info("Management reporter config: {}", x)
      x
  }

  override def preStart(): Unit = {
    super.preStart()

    config foreach { config =>
      if (config.initialUpdateDelayMsec < 0 || config.updateIntervalMsec <= 0) {
        log.warn("Inapropriate timing configuration detected. Disabling automatic container status reporting")
      } else {
        log.info("Activating automatic container status reporting with update interval [{}]", config.updateIntervalMsec)
        ticker = Some(context.system.scheduler.schedule(config.initialUpdateDelayMsec.milliseconds, config.updateIntervalMsec.milliseconds, self, Tick))
      }
    }

    context.system.eventStream.subscribe(context.self, classOf[ServiceInfo])
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(context.self)

    ticker.foreach(_.cancel())
    ticker = None

    super.postStop()
  }

  def receive: Receive = LoggingReceive {

    case Tick =>
      config.foreach { config =>
        // TODO: populate profile info
        val profiles = List()
        val info = ContainerInfo(idSvc.getUUID(), idSvc.getProperties().asScala.toMap, serviceInfos.values.toList, profiles)
        log.info("Performing report [{}].", info)
        val pipeline: HttpRequest => Future[ContainerRegistryResponseOK] = {
          sendReceive ~> unmarshal[ContainerRegistryResponseOK]
        }
        pipeline { Post(config.registryUrl, info) }.mapTo[ContainerRegistryResponseOK].pipeTo(self)
      }

    case ContainerRegistryResponseOK(id, actions) =>
      log.info("Reported [{}] to management node", id)
      if (!actions.isEmpty) {
        log.info("Received {} update actions from management node: {}", actions.size, actions)
        actions.foreach { action =>
          log.debug("Publishing event: {}", action)
          context.system.eventStream.publish(action)
        }
      }

    case serviceInfo @ ServiceInfo(name, ts, lifetime, props) =>
      log.debug("Update service info for: {}", name)
      serviceInfos += name -> serviceInfo

  }
}
