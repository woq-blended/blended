package blended.mgmt.agent.internal

import akka.actor.Actor
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import blended.container.context.api.ContainerContext
import blended.prickle.akka.http.PrickleSupport
import blended.updater.config._
import blended.util.logging.Logger
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationLong
import scala.util.Try

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
 * Only if all necessary configuration are set (currently `initialUpdateDelayMsec` and `updateIntervalMsec`),
 * the reporter sends information to the management container.
 * The target URL of the management container is configured with the `registryUrl` config entry.
 *
 */
trait MgmtReporter extends Actor with PrickleSupport {

  import MgmtReporter._
  import blended.updater.config.json.PrickleProtocol._

  ////////////////////
  // ABSTRACT
  protected val config: Try[MgmtReporterConfig]
  protected val ctContext: ContainerContext
  ////////////////////

  private case class MgmtReporterState(
      serviceInfos: Map[String, ServiceInfo],
      lastProfileInfo: ProfileInfo
  )

  private[this] lazy val log = Logger[MgmtReporter]

  implicit private lazy val eCtxt: ExecutionContext = context.system.dispatcher
  implicit private lazy val materializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(context.system))

  override def preStart(): Unit = {
    super.preStart()

    config foreach { config =>
      if (config.initialUpdateDelayMsec < 0 || config.updateIntervalMsec <= 0) {
        log.warn("Inappropriate timing configuration detected. Disabling automatic container status reporting")
      } else {
        log.info(s"Activating automatic container status reporting with update interval [${config.updateIntervalMsec}]")
        context.system.scheduler.scheduleOnce(config.initialUpdateDelayMsec.millis, self, Tick)
      }
    }

    context.system.eventStream.subscribe(context.self, classOf[ServiceInfo])
    context.system.eventStream.subscribe(context.self, classOf[ProfileInfo])

    context.become(
      reporting(
        MgmtReporterState(
          serviceInfos = Map.empty,
          lastProfileInfo = ProfileInfo(0L, Nil)
        )))
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(context.self)
    super.postStop()
  }

  private def handleTick(state: MgmtReporterState): Receive = {
    case Tick =>
      config.foreach { cfg =>
        val info = ContainerInfo(
          containerId = ctContext.uuid,
          properties = ctContext.properties,
          serviceInfos = state.serviceInfos.values.toList,
          profiles = state.lastProfileInfo.profiles,
          timestampMsec = System.currentTimeMillis()
        )
        log.debug(s"Performing report [$info].")

        val entity = Marshal(info).to[MessageEntity]

        val request = entity.map { entity =>
          HttpRequest(
            uri = cfg.registryUrl,
            method = HttpMethods.POST,
            entity = entity
          )
        }

        // TODO think about ssl
        request.flatMap { request =>
          Http(context.system).singleRequest(request)
        }

        context.system.scheduler.scheduleOnce(cfg.updateIntervalMsec.millis, self, Tick)
      }
  }

  private def handleEvents(state: MgmtReporterState): Receive = {
    // from event stream
    case serviceInfo @ ServiceInfo(name, _, _, _, _) =>
      log.debug(s"Update service info for: $name")
      context.become(reporting(state.copy(state.serviceInfos ++ Map(name -> serviceInfo))))

    // from event stream
    case pi @ ProfileInfo(timestamp, _) =>
      if (timestamp > state.lastProfileInfo.timeStamp) {
        log.debug("Update profile info to: " + pi)
        context.become(reporting(state.copy(lastProfileInfo = pi)))
      } else {
        log.debug(
          s"Ignoring profile info with timestamp [${timestamp}] which is older than "
            + s"[${state.lastProfileInfo.timeStamp}]: $pi")
      }
  }

  private def handleHttpRequests(state: MgmtReporterState): Receive = {
    case (response @ HttpResponse(status, _, entity, _), appliedIds: List[_]) =>
      status match {
        case StatusCodes.OK =>
          // As the server accepted also the list of applied update action IDs
          // we remove those from the list
          context.become(reporting(state))

          import akka.pattern.pipe

          // OK; unmarshal and process
          Unmarshal(entity).to[ContainerRegistryResponseOK].pipeTo(self)

        case _ =>
          log.warn(s"Non-OK response ${config.map(c => c.registryUrl).getOrElse("")} from node: $response")
          response.discardEntityBytes()
      }

    case ContainerRegistryResponseOK(id) =>
      log.debug(s"Reported [$id] to management node")
  }

  def receive: Receive = Actor.emptyBehavior

  private def reporting(state: MgmtReporterState): Receive = LoggingReceive {
    handleTick(state)
      .orElse(handleHttpRequests(state))
      .orElse(handleEvents(state))
  }
}

object MgmtReporter {

  object MgmtReporterConfig {
    def fromConfig(config: Config): Try[MgmtReporterConfig] = Try {
      import blended.util.config.Implicits._
      MgmtReporterConfig(
        registryUrl = config.getString("registryUrl"),
        updateIntervalMsec = config.getLong("updateIntervalMsec", 0),
        initialUpdateDelayMsec = config.getLong("initialUpdateDelayMsec", 0)
      )
    }
  }

  case class MgmtReporterConfig(
      registryUrl: String,
      updateIntervalMsec: Long,
      initialUpdateDelayMsec: Long
  ) {

    override def toString(): String =
      getClass().getSimpleName() +
        "(registryUrl=" + registryUrl +
        ",updateInetervalMsec=" + updateIntervalMsec +
        ",initialUpdateDelayMsec=" + initialUpdateDelayMsec +
        ")"
  }

  case object Tick

}
