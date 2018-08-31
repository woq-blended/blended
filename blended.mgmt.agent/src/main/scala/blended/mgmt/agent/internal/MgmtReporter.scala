package blended.mgmt.agent.internal

import scala.concurrent.duration.DurationLong
import scala.util.Try

import akka.actor.Actor
import akka.actor.Cancellable
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MessageEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import blended.prickle.akka.http.PrickleSupport
import blended.updater.config.ContainerInfo
import blended.updater.config.ContainerRegistryResponseOK
import blended.updater.config.ProfileInfo
import blended.updater.config.ServiceInfo
import com.typesafe.config.Config
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
trait MgmtReporter extends Actor with PrickleSupport {

  import MgmtReporter._
  import blended.updater.config.json.PrickleProtocol._

  ////////////////////
  // ABSTRACT
  protected val config: Try[MgmtReporterConfig]
  //
  protected def createContainerInfo: ContainerInfo
  ////////////////////

  ////////////////////
  // MUTABLE
  private[this] var _ticker: Option[Cancellable] = None
  private[this] var _serviceInfos: Map[String, ServiceInfo] = Map()
  private[this] var _profileInfo: ProfileInfo = ProfileInfo(0L, Nil)
  ////////////////////

  private[this] lazy val log = Logger[MgmtReporter]

  implicit private[this] lazy val eCtxt = context.system.dispatcher
  implicit private[this] lazy val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  protected def serviceInfos: Map[String, ServiceInfo] = _serviceInfos

  protected def profileInfo: ProfileInfo = _profileInfo

  override def preStart(): Unit = {
    super.preStart()

    config foreach { config =>
      if (config.initialUpdateDelayMsec < 0 || config.updateIntervalMsec <= 0) {
        log.warn("Inapropriate timing configuration detected. Disabling automatic container status reporting")
      } else {
        log.info(s"Activating automatic container status reporting with update interval [${config.updateIntervalMsec}]")
        _ticker = Some(context.system.scheduler.schedule(config.initialUpdateDelayMsec.milliseconds, config.updateIntervalMsec.milliseconds, self, Tick))
      }
    }

    context.system.eventStream.subscribe(context.self, classOf[ServiceInfo])
    context.system.eventStream.subscribe(context.self, classOf[ProfileInfo])
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(context.self)

    _ticker.foreach(_.cancel())
    _ticker = None

    super.postStop()
  }

  def receive: Receive = LoggingReceive {

    case Tick =>
      config.foreach { config =>

        val info = createContainerInfo
        log.debug(s"Performing report [${info}].")

        val entity = Marshal(info).to[MessageEntity]

        val request = entity.map { entity =>
          HttpRequest(
            uri = config.registryUrl,
            method = HttpMethods.POST,
            entity = entity
          )
        }

        // TODO think about ssl
        val responseFuture = request.flatMap { request =>
          Http(context.system).singleRequest(request)
        }

        import akka.pattern.pipe
        responseFuture.pipeTo(self)
      }

    case response @ HttpResponse(status, headers, entity, protocol) =>
      status match {
        case StatusCodes.OK =>
          import akka.pattern.pipe

          // OK; unmarshal and process
          Unmarshal(entity).to[ContainerRegistryResponseOK].pipeTo(self)

        case _ =>
          log.warn(s"Non-OK response ${config.map(c => s"(${c.registryUrl})").getOrElse()} from node: ${response}")
          response.discardEntityBytes()
      }

    case ContainerRegistryResponseOK(id, actions) =>
      log.debug(s"Reported [${id}] to management node")
      if (!actions.isEmpty) {
        log.info(s"Received ${actions.size} update actions from management node: ${actions}")
        actions.foreach { action =>
          log.debug(s"Publishing event: ${action}")
          context.system.eventStream.publish(action)
        }
      }

    // from event stream
    case serviceInfo @ ServiceInfo(name, svcType, ts, lifetime, props) =>
      log.debug(s"Update service info for: ${name}")
      _serviceInfos += name -> serviceInfo

    // from event stream
    case pi @ ProfileInfo(timestamp, _) =>
      if (timestamp > _profileInfo.timeStamp) {
        log.debug("Update profile info to: " + pi)
        _profileInfo = pi
      } else {
        log.debug(s"Ingnoring profile info with timestamp [${timestamp.underlying()}] which is older than [${_profileInfo.timeStamp.underlying()}]: ${pi}")
      }
  }
}

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
    initialUpdateDelayMsec: Long
  ) {

    override def toString(): String = s"${getClass().getSimpleName()}(registryUrl=${registryUrl},updateInetervalMsec=${updateIntervalMsec},initialUpdateDelayMsec=${initialUpdateDelayMsec})"
  }

  case object Tick

}
