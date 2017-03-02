package blended.updater

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.Props
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.routing.BalancingPool
import akka.util.Timeout
import blended.updater.config.UpdateAction
import blended.updater.config.{ AddRuntimeConfig => UAAddRuntimeConfig }
import blended.updater.config.{ AddOverlayConfig => UAAddOverlayConfig }
import blended.updater.config.{ ActivateProfile => UAActivateProfile }
import blended.updater.config.{ StageProfile => UAStageProfile }
import blended.updater.config._
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import org.slf4j.LoggerFactory

import scala.collection.immutable._
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class Updater(
  installBaseDir: File,
  profileActivator: ProfileActivator,
  restartFramework: () => Unit,
  config: UpdaterConfig,
  launchedProfileDir: Option[File],
  launchedProfileId: Option[ProfileId])
    extends Actor
    with ActorLogging {

  import Updater._

  private[this] val log = LoggerFactory.getLogger(classOf[Updater])

  val artifactDownloader = context.actorOf(
    BalancingPool(config.artifactDownloaderPoolSize).props(ArtifactDownloader.props()),
    "artifactDownloader")
  val unpacker = context.actorOf(
    BalancingPool(config.unpackerPoolSize).props(Unpacker.props()),
    "unpacker")

  /////////////////////
  // MUTABLE
  // requestId -> State
  private[this] var stagingInProgress: Map[String, State] = Map()

  private[this] var profiles: Map[ProfileId, LocalProfile] = Map()

  private[this] var overlayConfigs: Set[OverlayConfig] = Set()
  private[this] var runtimeConfigs: Set[LocalRuntimeConfig] = Set()

  private[this] var tickers: Seq[Cancellable] = Nil
  ////////////////////

  private[this] def stageInProgress(state: State): Unit = {
    val id = state.requestId
    val config = state.config
    val progress = state.progress()
    log.debug("Progress: {} for reqestId: {}", progress, id)

    // TODO: generate config files (overlay)

    if (state.artifactsToDownload.isEmpty && state.issues.isEmpty && !state.pendingArtifactsToUnpack.isEmpty) {
      // start unpacking
      state.pendingArtifactsToUnpack.foreach { a =>
        unpacker ! Unpacker.Unpack(a.reqId, self, a.file, config.baseDir)
      }
      stageInProgress(state.copy(
        artifactsToUnpack = state.pendingArtifactsToUnpack,
        pendingArtifactsToUnpack = Nil
      ))
    } else if (state.artifactsToDownload.isEmpty && state.artifactsToUnpack.isEmpty) {

      val finalIssues = if (state.issues.isEmpty) {
        val previousRuntimeConfig = findActiveConfig()
        val result = RuntimeConfigCompanion.createPropertyFile(state.config, previousRuntimeConfig)
        result match {
          case None =>
            // nothing to do, is ok
            List.empty
          case Some(Success(_)) =>
            // ok
            List.empty
          case Some(Failure(e)) =>
            // could not create properties file
            List(s"Could not create properties file: ${e.getMessage()}")
        }
      } else state.issues

      stagingInProgress = stagingInProgress.filterKeys(id != _)
      val (profileState, msg) = finalIssues match {
        case Seq() => LocalProfile.Staged -> OperationSucceeded(id)
        case issues => LocalProfile.Invalid(issues) -> OperationFailed(id, issues.mkString("; "))
      }
      profiles += state.profileId -> LocalProfile(state.config, state.overlays, profileState)
      state.requestActor ! msg
    } else {
      stagingInProgress += id -> state
    }
  }

  def findConfig(id: ProfileId): Option[LocalRuntimeConfig] = profiles.get(id).map(_.config)

  @deprecated
  def findActiveConfig(): Option[LocalRuntimeConfig] = launchedProfileDir.flatMap { dir =>
    val absDir = dir.getAbsoluteFile()
    profiles.values.find { profile =>
      profile.config.baseDir.getAbsoluteFile() == absDir
    }.map(_.config)
  }

  def findActiveProfile(): Option[Profile] = {
    // FIXME
    None
  }

  def findLocalResource(name: String, sha1Sum: String): Option[File] =
    profiles.values.filter { profile =>
      // only valid profiles
      profile.state == LocalProfile.Staged
    }.
      flatMap { profile =>
        profile.bundles.filter { b =>
          // same sha1sum
          b.sha1Sum == Some(sha1Sum)
        }.
          filter { b =>
            val location = profile.config.bundleLocation(b.artifact)
            // same file name and file exists
            location.getName() == name && location.exists()
          }.
          map { b =>
            // extract location
            profile.config.bundleLocation(b.artifact)
          }.headOption
      }.headOption

  private[this] def nextId(): String = UUID.randomUUID().toString()

  case object PublishServiceInfo

  override def preStart(): Unit = {
    log.info("Initiating initial scanning for profiles")
    self ! Scan

    if (config.autoStagingIntervalMSec > 0) {
      log.info(s"Enabling auto-staging with interval [${config.autoStagingIntervalMSec}] and initial delay [${config.autoStagingDelayMSec}]")
      implicit val eCtx = context.system.dispatcher
      tickers +:= context.system.scheduler.schedule(
        Duration(config.autoStagingDelayMSec, TimeUnit.MILLISECONDS),
        Duration(config.autoStagingIntervalMSec, TimeUnit.MILLISECONDS)) {
          self ! StageNextRuntimeConfig(nextId())
        }
    } else {
      log.info(s"Auto-staging is disabled")
    }

    if (config.serviceInfoIntervalMSec > 0) {
      log.info(s"Enabling service info publishing [${config.serviceInfoIntervalMSec}] and lifetime [${config.serviceInfoLifetimeMSec}]")
      implicit val eCtx = context.system.dispatcher
      tickers +:= context.system.scheduler.schedule(
        Duration(100, TimeUnit.MILLISECONDS),
        Duration(config.serviceInfoIntervalMSec, TimeUnit.MILLISECONDS)) {
          self ! PublishServiceInfo
        }
    } else {
      log.info("Publishing of service infos is disabled")
    }

    context.system.eventStream.subscribe(context.self, classOf[UpdateAction])

    super.preStart()
  }

  override def postStop(): Unit = {

    context.system.eventStream.unsubscribe(context.self)

    tickers.foreach { t =>
      log.info("Disabling ticker: {}", t)
      t.cancel()
    }
    tickers = Nil
    super.postStop()
  }

  def handleUpdateAction(event: UpdateAction): Unit = event match {
    case UAAddRuntimeConfig(runtimeConfig) =>
      log.debug("Received add runtime config request (via event stream) for {}-{}",
        Array(runtimeConfig.name, runtimeConfig.version))

      implicit val ec = context.system.dispatcher
      val timeout = new Timeout(10, TimeUnit.MINUTES)

      self.ask(AddRuntimeConfig(nextId(), runtimeConfig))(timeout).onComplete { x =>
        log.debug("Finished add runtime config request (via event stream) for {}-{} with result: {}",
          Array(runtimeConfig.name, runtimeConfig.version, x))
      }

    case UAAddOverlayConfig(overlayConfig) =>
      log.debug("Received add overlay config request (via event stream) for {}-{}",
        Array(overlayConfig.name, overlayConfig.version))

      implicit val ec = context.system.dispatcher
      val timeout = new Timeout(10, TimeUnit.MINUTES)

      self.ask(AddOverlayConfig(nextId(), overlayConfig))(timeout).onComplete { x =>
        log.debug("Finished add overlay config request (via event stream) for {}-{} with result: {}",
          Array(overlayConfig.name, overlayConfig.version, x))
      }

    case UAStageProfile(name, version, overlayRefs) =>
      log.debug("Received stage profile request (via event stream) for {}-{} and overlays: ",
        Array(name, version, overlayRefs))

      implicit val ec = context.system.dispatcher
      val timeout = new Timeout(10, TimeUnit.MINUTES)

      self.ask(StageProfile(nextId(), name, version, overlayRefs))(timeout).onComplete { x =>
        log.debug("Finished stage profile request (via event stream) for {}-{} and overlays {} with result: {}",
          Array(name, version, overlayRefs, x))
      }

    case UAActivateProfile(name, version, overlayRefs) =>
      log.debug("Received activate profile request (via event stream) for {}-{} and overlays: {}",
        Array(name, version, overlayRefs))

      implicit val ec = context.system.dispatcher
      val timeout = new Timeout(10, TimeUnit.MINUTES)

      self.ask(ActivateProfile(nextId(), name, version, overlayRefs))(timeout).onComplete { x =>
        log.debug("Finished activation profile request (via event stream) for {}-{} and overlays {} with result: {}",
          Array(name, version, overlayRefs, x))
      }
  }

  def handleProtocol(msg: Protocol): Unit = msg match {

    case GetOverlays(reqId) =>
      sender() ! Result(reqId, overlayConfigs)

    case GetRuntimeConfigs(reqId) =>
      sender() ! Result(reqId, runtimeConfigs)

    case GetProfiles(reqId) =>
      sender() ! Result(reqId, profiles.values.toSet)

    case GetProfileIds(reqId) =>
      sender() ! Result(reqId, profiles.keySet)

    case AddOverlayConfig(reqId, config) =>
      overlayConfigs.find(o => o.name == config.name && o.version == config.version) match {
        case Some(existing) if existing != config =>
          sender() ! OperationFailed(reqId, s"A different overlay with same name and version (${config.name}-${config.version}) is already registered")
        case _ =>
          val confFile = new File(installBaseDir.getParentFile(), s"overlays/${config.name}-${config.version}.conf")
          confFile.getParentFile().mkdirs()
          ConfigWriter.write(OverlayConfigCompanion.toConfig(config), confFile, None)
          overlayConfigs += config
          sender ! OperationSucceeded(reqId)
      }

    case AddRuntimeConfig(reqId, config) =>
      runtimeConfigs.find(r => r.runtimeConfig.name == config.name && r.runtimeConfig.version == config.version) match {
        case Some(existing) if existing.runtimeConfig != config =>
          sender() ! OperationFailed(reqId, s"A different overlay with same name and version (${config.name}-${config.version}) is already registered")
        case _ =>
          val confFile = new File(installBaseDir, s"${config.name}/${config.version}/profile.conf")
          config.resolve() match {
            case Success(resolved) =>
              confFile.getParentFile().mkdirs()
              ConfigWriter.write(RuntimeConfigCompanion.toConfig(config), confFile, None)
              runtimeConfigs += LocalRuntimeConfig(baseDir = confFile.getParentFile(), resolvedRuntimeConfig = resolved)
              sender() ! OperationSucceeded(reqId)
            case Failure(e) =>
              sender() ! OperationFailed(reqId, s"Given runtime config can't be resolved: ${e.getMessage}")
          }
      }

    case StageNextRuntimeConfig(reqId) =>
      if (stagingInProgress.isEmpty) {
        profiles.toIterator.collect {
          case (id @ ProfileId(name, version, overlays), LocalProfile(_, _, LocalProfile.Pending(_))) =>
            log.info("About to auto-stage profile {}", id)
            self ! StageProfile(nextId(), name, version, overlays = overlays)
        }.take(1)
      }

    case StageProfile(reqId, name, version, overlayRefs) =>
      val profileId = ProfileId(name, version, overlayRefs)

      profiles.get(profileId).orElse {
        Try {
          val localRuntimeConfig = runtimeConfigs.find(r => r.runtimeConfig.name == name && r.runtimeConfig.version == version) match {
            case None => sys.error(s"No such runtime config found: ${name}-${version}")
            case Some(rc) => rc
          }

          val overlays = overlayRefs.map(ref => ref -> overlayConfigs.find(o => o.overlayRef == ref))
          overlays.find(v => v._2 == None).map { v =>
            sys.error(s"No such overlay config found: ${v._1.name}-${v._1.version}")
          }

          val localOverlays = LocalOverlays(overlays.map(_._2.get), localRuntimeConfig.baseDir)

          LocalProfile(localRuntimeConfig, localOverlays, LocalProfile.Pending(List("Newly staged")))

        } match {
          case Failure(e) =>
            sender() ! OperationFailed(reqId, e.getMessage())
            None
          case Success(p) => Some(p)
        }

      } map {
        case profile  @ LocalProfile(_, _, LocalProfile.Staged) =>
          // already staged
          log.debug("Profile already staged: {}", profile.profileId)
          sender() ! OperationSucceeded(reqId)

        case profile @ LocalProfile(config, localOverlays, state) =>
          log.debug("About to stage profile: {}", profile.profileId)
          val reqActor = sender()

          val validErrors = localOverlays.validate()
          if (!validErrors.isEmpty) {
            reqActor ! OperationFailed(reqId, "Overlay configuration contains errors: " + validErrors.mkString("; "))
          } else {
            val overlayFiles = localOverlays.materialize()
            val overlayFile = LocalOverlays.preferredConfigFile(localOverlays.overlayRefs, localOverlays.profileDir)
            log.debug("About to save applied overlays config to: {}", overlayFile)
            ConfigWriter.write(LocalOverlays.toConfig(localOverlays), overlayFile, None)

            if (stagingInProgress.contains(reqId)) {
              log.error("Duplicate id detected. Dropping request: {}", msg)
            } else {
              // analyze config
              overlayFiles match {
                case Failure(e) =>
                  reqActor ! OperationFailed(reqId, "Overlay configuration contains errors: " + e.getMessage())
                case Success(generateFiles) =>
                  val artifacts = config.resolvedRuntimeConfig.allBundles.map { b =>
                    ArtifactInProgress(nextId(), b.artifact, config.bundleLocation(b))
                  } ++
                    config.runtimeConfig.resources.map { r =>
                      ArtifactInProgress(nextId(), r, config.resourceArchiveLocation(r))
                    }

                  val pendingUnpacks = config.runtimeConfig.resources.map { r =>
                    ArtifactInProgress(nextId(), r, config.resourceArchiveLocation(r))
                  }

                  artifacts.foreach { a =>
                    artifactDownloader ! ArtifactDownloader.Download(a.reqId, a.artifact, a.file)
                  }

                  stageInProgress(State(
                    requestId = reqId,
                    requestActor = reqActor,
                    config = config,
                    artifactsToDownload = artifacts,
                    pendingArtifactsToUnpack = pendingUnpacks,
                    artifactsToUnpack = List.empty,
                    overlays = localOverlays,
                    issues = List.empty))

              }
            }
          }
      }

    case ActivateProfile(reqId, name, version, overlays) =>
      val profileId = ProfileId(name, version, overlays)
      log.debug("Requested activate profile with id: {}", profileId)
      val requestingActor = sender()
      profiles.get(profileId) match {
        case Some(LocalProfile(_, _, LocalProfile.Staged)) =>
          // write config
          log.debug("About to activate new profile for next startup: {}", profileId)
          val success = profileActivator(name, version, profileId.overlays)
          if (success) {
            requestingActor ! OperationSucceeded(reqId)
            restartFramework()
          } else {
            requestingActor ! OperationFailed(reqId, "Could not update next startup profile")
          }
        case r =>
          log.debug("Could not find staged profile with id {} but: {}", Array(profileId, r): _*)
          log.trace("All known profiles: {}", profiles.keySet)
          requestingActor ! OperationFailed(reqId, "No such staged runtime configuration found")
      }

    case GetProgress(reqId) =>
      stagingInProgress.get(reqId) match {
        case Some(state) => sender ! Progress(reqId, state.progress())
        case None => sender() ! OperationFailed(reqId, "Unknown request ID")
      }

  }

  def scanForOverlayConfigs(): List[OverlayConfig] = {
    val overlayBaseDir = new File(installBaseDir.getParentFile(), "overlays")
    ProfileFsHelper.scanForOverlayConfigs(overlayBaseDir)
  }

  def scanForRuntimeConfigs(): List[LocalRuntimeConfig] = {
    ProfileFsHelper.scanForRuntimeConfigs(installBaseDir)
  }

  def scanForProfiles(runtimeConfigs: Option[List[LocalRuntimeConfig]] = None): List[LocalProfile] = {
    ProfileFsHelper.scanForProfiles(installBaseDir, runtimeConfigs)
  }

  override def receive: Actor.Receive = LoggingReceive {

    // from event stream
    case e: UpdateAction => handleUpdateAction(e)

    // direct protocol
    case p: Protocol => handleProtocol(p)

    case Scan =>
      overlayConfigs = scanForOverlayConfigs().toSet
      val rcs = scanForRuntimeConfigs()
      runtimeConfigs = rcs.toSet

      val fullProfiles = scanForProfiles(Option(rcs))
      profiles = fullProfiles.map { profile => profile.profileId -> profile }.toMap
      log.debug("Profiles (after scan): {}", profiles)

    case PublishServiceInfo =>
      log.debug("About to gather service infos")

      // FIXME: better represent overlays!!!
      val props = Map(
        "profile.active" -> findActiveConfig().map(lrc => s"${lrc.runtimeConfig.name}-${lrc.runtimeConfig.version}").getOrElse(""),
        "profiles.valid" -> profiles.collect {
          case (ProfileId(name, version, overlays), LocalProfile(config, localOverlays, LocalProfile.Staged)) => s"$name-$version"
        }.mkString(","),
        "profiles.invalid" -> profiles.collect {
          case (ProfileId(name, version, overlays), LocalProfile(config, localOverlays, LocalProfile.Invalid(_))) => s"$name-$version"
        }.mkString(","),
        "profiles.pending" -> profiles.collect {
          case (ProfileId(name, version, overlays), LocalProfile(config, localOverlays, LocalProfile.Pending(_))) => s"$name-$version"
        }.mkString(",")
      ).filter { case (k, v) => !v.isEmpty() }

      val serviceInfo = ServiceInfo(
        name = context.self.path.toString,
        serviceType = "Updater",
        timestampMsec = System.currentTimeMillis(),
        lifetimeMsec = config.serviceInfoLifetimeMSec,
        props = props
      )
      log.debug("About to publish service info: {}", serviceInfo)
      context.system.eventStream.publish(serviceInfo)

    case msg: ArtifactDownloader.Reply =>
      val foundProgress = stagingInProgress.values.flatMap { state =>
        state.artifactsToDownload.find { bip => bip.reqId == msg.requestId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown download id {}", msg.requestId)
        case (state, bundleInProgress) :: _ =>
          val newArtifactsToDownload = state.artifactsToDownload.filter(bundleInProgress != _)
          msg match {
            case ArtifactDownloader.DownloadFinished(_) =>
              stageInProgress(state.copy(
                artifactsToDownload = newArtifactsToDownload
              ))
            case ArtifactDownloader.DownloadFailed(_, error) =>
              stageInProgress(state.copy(
                artifactsToDownload = newArtifactsToDownload,
                issues = s"Download failed for ${bundleInProgress.file} (${error.getClass().getSimpleName()}: ${error})" +: state.issues
              ))
          }
      }

    case msg: Unpacker.UnpackReply =>
      val foundProgress = stagingInProgress.values.flatMap { state =>
        state.artifactsToUnpack.find { a => a.reqId == msg.reqId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown unpack process {}", msg.reqId)
        case (state, artifact) :: _ =>
          val artifactsToUnpack = state.artifactsToUnpack.filter(artifact != _)
          msg match {
            case Unpacker.UnpackingFinished(_) =>
              state.config.createResourceArchiveTouchFile(artifact.artifact, artifact.artifact.sha1Sum) match {
                case Success(file) =>
                  stageInProgress(state.copy(
                    artifactsToUnpack = artifactsToUnpack
                  ))
                case Failure(e) =>
                  stageInProgress(state.copy(
                    artifactsToUnpack = artifactsToUnpack,
                    issues = s"Could not create unpacked-marker file for resource file ${artifact.file} (${e.getClass().getSimpleName()}: ${e.getMessage()})" +: state.issues
                  ))
              }
            case Unpacker.UnpackingFailed(_, e) =>
              stageInProgress(state.copy(
                artifactsToUnpack = artifactsToUnpack,
                issues = s"Could not unpack file ${artifact.file} (${e.getClass().getSimpleName()}: ${e.getMessage()})" +: state.issues
              ))
          }
      }

  }

}

object Updater {

  /**
   * Supported Messages by the [[Updater]] actor.
   */
  sealed trait Protocol {
    def requestId: String
  }

  //  /**
  //   * Request lists of runtime configurations. Replied with [RuntimeConfigs].
  //   * FIXME: rename to GetProfiles
  //   */
  //  final case class GetRuntimeConfigs(override val requestId: String) extends Protocol

  final case class GetRuntimeConfigs(override val requestId: String) extends Protocol

  final case class GetOverlays(override val requestId: String) extends Protocol

  /**
   * Get all known profiles.
   * Reply: Result[Set[Profile]], OperationFailed
   */
  final case class GetProfiles(override val requestId: String) extends Protocol

  /**
   * Get all known profile ids.
   * Reply: Result[Set[ProfileId]], OperationFailed
   */
  final case class GetProfileIds(override val requestId: String) extends Protocol

  final case class GetProgress(override val requestId: String) extends Protocol

  final case class AddRuntimeConfig(override val requestId: String, runtimeConfig: RuntimeConfig) extends Protocol

  final case class AddOverlayConfig(override val requestId: String, overlayConfig: OverlayConfig) extends Protocol

  // explicit trigger staging of a config, but idea is to automatically stage not already staged configs when idle
  final case class StageProfile(override val requestId: String, name: String, version: String, overlays: List[OverlayRef]) extends Protocol

  final case class ActivateProfile(override val requestId: String, name: String, version: String, overlays: List[OverlayRef]) extends Protocol

  /**
   * Scans the profile directory for existing runtime configurations and replaces the internal state of this actor with the result.
   */
  private final case object Scan

  final case class StageNextRuntimeConfig(override val requestId: String) extends Protocol

  /**
   * Supported Replies by the [[Updater]] actor.
   */
  sealed trait Reply

  //  @deprecated
  //  final case class RuntimeConfigs(
  //    requestId: String,
  //    staged: Seq[LocalRuntimeConfig],
  //    pending: Seq[LocalRuntimeConfig],
  //    invalid: Seq[LocalRuntimeConfig])
  //      extends Reply {
  //    override def toString(): String = s"${getClass().getSimpleName()}(requestId=${requestId},staged=${staged},pending=${pending},invalid=${invalid})"
  //  }

  final case class Progress(requestId: String, progress: Int) extends Reply

  final case class Result[T](requestId: String, result: T) extends Reply

  final case class OperationSucceeded(requestId: String) extends Reply

  final case class OperationFailed(requestId: String, reason: String) extends Reply

  def props(
    baseDir: File,
    profileActivator: ProfileActivator,
    restartFramework: () => Unit,
    config: UpdaterConfig,
    launchedProfileDir: File = null,
    launchedProfileId: ProfileId = null): Props = {

    Props(new Updater(
      installBaseDir = baseDir,
      profileActivator = profileActivator,
      restartFramework = restartFramework,
      config,
      Option(launchedProfileDir),
      Option(launchedProfileId)
    ))
  }

  /**
   * A bundle in progress, e.g. downloading or verifying.
   */
  private case class ArtifactInProgress(reqId: String, artifact: Artifact, file: File)

  /**
   * Internal working state of in-progress stagings.
   */
  private case class State(
      requestId: String,
      requestActor: ActorRef,
      config: LocalRuntimeConfig,
      artifactsToDownload: List[ArtifactInProgress],
      pendingArtifactsToUnpack: List[ArtifactInProgress],
      artifactsToUnpack: List[ArtifactInProgress],
      overlays: LocalOverlays,
      issues: List[String]) {

    val profileId = ProfileId(config.runtimeConfig.name, config.runtimeConfig.version, overlays.overlayRefs)

    /**
     * The download/unpack progress in percent.
     */
    def progress(): Int = {
      val all = config.runtimeConfig.bundles.size
      val todos = artifactsToDownload.size
      if (all > 0)
        (100 / all) * (all - todos)
      else 100
    }

  }


}
