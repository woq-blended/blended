package blended.updater

import java.util.UUID
import java.io.File
import java.util.concurrent.TimeUnit
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.Props
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.routing.BalancingPool
import blended.launcher.config.LauncherConfig
import blended.updater.config.Artifact
import blended.updater.config.BundleConfig
import blended.updater.config.ConfigConverter
import blended.updater.config.ConfigWriter
import blended.updater.config.RuntimeConfig
import com.typesafe.config.ConfigFactory
import org.osgi.framework.BundleContext
import scala.collection.immutable._
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.typesafe.config.Config
import blended.updater.config.LocalRuntimeConfig
import com.typesafe.config.ConfigParseOptions
import blended.mgmt.base.ServiceInfo
import org.slf4j.LoggerFactory
import blended.updater.config.ResolvedRuntimeConfig
import blended.mgmt.base.UpdateAction
import blended.mgmt.base.{StageProfile => UAStageProfile}
import akka.util.Timeout
import blended.mgmt.base.ActivateProfile
import blended.updater.config.OverlayConfig
import scala.collection.immutable
import blended.updater.config.OverlayRef
import blended.updater.config.LocalOverlays

class Updater(
  installBaseDir: File,
  profileUpdater: (String, String) => Boolean,
  restartFramework: () => Unit,
  config: UpdaterConfig,
  launchedProfileDir: Option[File])
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

  private[this] var profiles: Map[ProfileId, Profile] = Map()

  private[this] var overlayConfigs: Set[OverlayConfig] = Set()

  private[this] var tickers: Seq[Cancellable] = Nil
  ////////////////////

  private[this] def stageInProgress(state: State): Unit = {
    val id = state.requestId
    val config = state.config
    val progress = state.progress()
    log.debug("Progress: {} for reqestId: {}", progress, id)

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
        val result = RuntimeConfig.createPropertyFile(state.config, previousRuntimeConfig)
        result match {
          case None =>
            // nothing to do, is ok
            Seq()
          case Some(Success(_)) =>
            // ok
            Seq()
          case Some(Failure(e)) =>
            // could not create properties file
            Seq(s"Could not create properties file: ${e.getMessage()}")
        }
      } else state.issues

      stagingInProgress = stagingInProgress.filterKeys(id != _)
      val (profileState, msg) = finalIssues match {
        case Seq() => Profile.Staged -> OperationSucceeded(id)
        case issues => Profile.Invalid(issues) -> OperationFailed(id, issues.mkString("; "))
      }
      profiles += state.profileId -> Profile(state.config, state.overlays, profileState)
      state.requestActor ! msg
    } else {
      stagingInProgress += id -> state
    }
  }

  def findConfig(id: ProfileId): Option[LocalRuntimeConfig] = profiles.get(id).map(_.config)

  def findActiveConfig(): Option[LocalRuntimeConfig] = launchedProfileDir.flatMap { dir =>
    val absDir = dir.getAbsoluteFile()
    profiles.values.find { profile =>
      profile.config.baseDir.getAbsoluteFile() == absDir
    }.map(_.config)
  }

  def findLocalResource(name: String, sha1Sum: String): Option[File] =
    profiles.values.filter { profile =>
      // only valid profiles
      profile.state == Profile.Staged
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
    self ! ScanForRuntimeConfigs

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
    case UAStageProfile(runtimeConfig, overlayConfigs) =>
      log.debug("Received stage profile request (via event stream) for {}-{}",
        Array(runtimeConfig.name, runtimeConfig.version))

      implicit val ec = context.system.dispatcher
      val timeout = new Timeout(10, TimeUnit.MINUTES)

      self.ask(AddRuntimeConfig(nextId(), runtimeConfig))(timeout).onComplete { x =>
        log.debug("Finished stage profile request (via event stream) for {}-{} with result: {}",
          Array(runtimeConfig.name, runtimeConfig.version, x))
      }

    case ActivateProfile(name, version, overlays) =>
      log.debug("Received activate profile request (via event stream) for {}-{}",
        Array(name, version))

      implicit val ec = context.system.dispatcher
      val timeout = new Timeout(10, TimeUnit.MINUTES)

      self.ask(ActivateRuntimeConfig(nextId(), name, version, overlays))(timeout).onComplete { x =>
        log.debug("Finished activation profile request (via event stream) for {}-{} with result: {}",
          Array(name, version, x))
      }
  }

  def handleProtocol(msg: Protocol): Unit = msg match {

    // FIXME: add GetProfiles
    case GetRuntimeConfigs(reqId) =>
      val ps = profiles.values.toList
      sender() ! RuntimeConfigs(reqId,
        staged = ps.collect { case Profile(config, ignoredOverlays, Profile.Staged) => config },
        pending = ps.collect { case Profile(config, ignoredOverlays, Profile.Pending(_)) => config },
        invalid = ps.collect { case Profile(config, ignoredOverlays, Profile.Invalid(_)) => config }
      )

    case AddOverlayConfig(reqId, config) =>
    // TODO: persist overlay

    case AddRuntimeConfig(reqId, config) =>
      val id = ProfileId(config.name, config.version, Set())
      val req = sender()
      findConfig(id) match {
        case None =>
          val dir = new File(new File(installBaseDir, config.name), config.version)
          dir.mkdirs()

          val confFile = new File(dir, "profile.conf")

          val overlays = LocalOverlays(Set(), dir)

          config.resolve() match {
            case Success(resolved) =>
              ConfigWriter.write(RuntimeConfig.toConfig(config), confFile, None)
              profiles += id -> Profile(LocalRuntimeConfig(resolved, dir), overlays, Profile.Pending(Seq("Never checked")))
              req ! OperationSucceeded(reqId)
            case Failure(e) =>
              req ! OperationFailed(reqId, s"Given runtime config can't be resolved: ${e.getMessage}")
          }

        // TODO: also create overlay files

        case Some(`config`) =>
          req ! OperationSucceeded(reqId)
        case Some(collision) =>
          req ! OperationFailed(reqId, "A different runtime config is already present under the same coordinates")
      }

    case StageNextRuntimeConfig(reqId) =>
      if (stagingInProgress.isEmpty) {
        profiles.toIterator.collect {
          case (id @ ProfileId(name, version, overlays), Profile(_, _, Profile.Pending(_))) =>
            log.info("About to auto-stage profile {}", id)
            self ! StageProfile(nextId(), name, version, overlays = overlays)
        }.take(1)
      }

    case StageProfile(reqId, name, version, overlays) =>
      // TODO: process overlays

      profiles.get(ProfileId(name, version, overlays)) match {
        case None =>
          sender() ! OperationFailed(reqId, s"No such runtime configuration found: ${name} ${version}")

        case Some(Profile(config, localOverlays, Profile.Staged)) =>
          // already staged
          sender() ! OperationSucceeded(reqId)

        case Some(Profile(config, localOverlays, state)) =>
          val reqActor = sender()
          if (stagingInProgress.contains(reqId)) {
            log.error("Duplicate id detected. Dropping request: {}", msg)
          } else {
            log.info("About to stage installation: {}", config)

            // analyze config

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
              artifactsToUnpack = Seq(),
              overlays = localOverlays,
              issues = Seq()))
          }

      }

    case ActivateRuntimeConfig(reqId, name, version, overlays) =>
      val requestingActor = sender()
      profiles.get(ProfileId(name, version, overlays)) match {
        case Some(Profile(LocalRuntimeConfig(config, dir), LocalOverlays(overlayConfigs, oDir), Profile.Staged)) =>
          // write config
          log.debug("About to activate new profile for next startup: {}-{}", Array(name, version))
          val success = profileUpdater(name, version)
          if (success) {
            requestingActor ! OperationSucceeded(reqId)
            restartFramework()
          } else {
            requestingActor ! OperationFailed(reqId, "Could not update next startup profile")
          }
        case _ =>
          requestingActor ! OperationFailed(reqId, "No such staged runtime configuration found")
      }

    case GetProgress(reqId) =>
      stagingInProgress.get(reqId) match {
        case Some(state) => sender ! Progress(reqId, state.progress())
        case None => sender() ! UnknownRequestId(reqId)
      }

  }

  def scanForOverlayConfigs(): List[OverlayConfig] = {
    val overlayBaseDir = new File(installBaseDir.getParentFile(), "overlays")
    log.debug("Scanning for overlays configs in: {}", overlayBaseDir)

    val confFiles = Option(overlayBaseDir.listFiles).getOrElse(Array()).
      filter(f => f.isFile() && f.getName().endsWith(".conf"))

    val configs = confFiles.toList.flatMap { file =>
      Try { ConfigFactory.parseFile(file).resolve() }.
        flatMap(OverlayConfig.read) match {
          case Success(overlayConfig) =>
            List(overlayConfig)
          case Failure(e) =>
            log.error("Could not parse overlay config file: {}", Array(file, e))
            List()
        }
    }

    log.info("Found overlay configs : {}", configs)

    configs

  }

  def scanForRuntimeConfigs(): List[LocalRuntimeConfig] = {
    log.debug("Scanning for runtime configs in {}", installBaseDir)

    val configFiles = Option(installBaseDir.listFiles).getOrElse(Array()).toList.
      flatMap { nameDir =>
        Option(nameDir.listFiles).getOrElse(Array()).toList.
          flatMap { versionDir =>
            val profileFile = new File(versionDir, "profile.conf")
            if (profileFile.exists()) Some(profileFile)
            else None
          }
      }

    log.info("Found potential runtime configs : {}", configFiles)

    // read configs
    val runtimeConfigs = configFiles.flatMap { runtimeConfigFile =>
      Try {
        val versionDir = runtimeConfigFile.getParentFile()
        val version = versionDir.getName()
        val name = versionDir.getParentFile.getName()

        val config = ConfigFactory.parseFile(runtimeConfigFile, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
        val resolved = ResolvedRuntimeConfig(RuntimeConfig.read(config).get)
        val local = LocalRuntimeConfig(baseDir = versionDir, resolvedRuntimeConfig = resolved)

        // consistency checks
        if (local.runtimeConfig.name == name && local.runtimeConfig.version == version) {
          List(local)
        } else {
          log.warn(s"Profile name and version do not match directory names: ${runtimeConfigFile}")
          List()
        }
      }.getOrElse {
        log.warn(s"Could not read profile file: ${runtimeConfigFile}")
        List()
      }
    }
    log.info(s"Found runtime configs: ${runtimeConfigs}")

    runtimeConfigs
  }

  def scanForProfiles(): List[Profile] = {
    log.debug("Scanning for profiles in: {}", installBaseDir)

    val runtimeConfigsWithIssues = scanForRuntimeConfigs().flatMap { localConfig =>
      val issues = localConfig.validate(
        includeResourceArchives = false,
        explodedResourceArchives = true,
        checkPropertiesFile = true).toList
      log.debug("Found runtime config: {}", localConfig)
      log.debug("Runtime config issues: {}", issues)
      List(localConfig -> issues)

    }

    log.debug(s"Runtime configs (with issues): ${runtimeConfigsWithIssues}")

    def profileState(issues: Seq[String]): Profile.ProfileState = issues match {
      case Seq() => Profile.Staged
      case issues => Profile.Pending(issues)
    }

    val fullProfiles = runtimeConfigsWithIssues.flatMap {
      case (localRuntimeConfig, issues) =>
        val profileDir = localRuntimeConfig.baseDir

        // scan for overlays
        val overlayDir = new File(profileDir, "overlays")
        val overlayFiles = Option(overlayDir.listFiles()).getOrElse(Array()).filter(f => f.getName().endsWith(".conf")).toList
        if (overlayFiles.isEmpty) {
          log.info("Could not found any overlay configs for profile: {}", localRuntimeConfig.profileFileLocation)
          log.info("Migrating legacy profile. Generating base overlay config")
          // We create a transient base overlay
          // TODO: Remove timely
          List(Profile(localRuntimeConfig, LocalOverlays(Set(), profileDir), profileState(issues)))

        } else overlayFiles.flatMap { file =>
          Try {
            ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
          }.flatMap { c =>
            LocalOverlays.read(c, profileDir)
          } match {
            case Failure(e) =>
              log.error(s"Could not load overlay config file: ${file}", e)
              None
            case Success(localOverlays) =>
              val canonicalFile = LocalOverlays.preferredConfigFile(localOverlays.overlays.map(_.overlayRef), profileDir)
              if (canonicalFile != file) {
                log.error("Skipping found overlays file because filename does not match the expected file name: {}", file)
                List()
              } else {
                val overlayIssues = localOverlays.validate() match {
                  case Seq() =>
                    // no conflicts, now check if already materialized
                    if (localOverlays.isMaterialized()) {
                      List("Overlays not materialized")
                    } else {
                      List()
                    }
                  case issues =>
                    log.error("Skipping found overlays file because it is not valid: {}. Issue: {}",
                      Array(file, issues.mkString(" / ")))
                    issues.toList
                }
                log.debug("Found overlay:", localOverlays)
                log.debug("Found overlay issues: {}", issues)
                List(Profile(localRuntimeConfig, localOverlays, profileState(issues ::: overlayIssues)))
              }
          }
        }
    }

    fullProfiles
  }

  override def receive: Actor.Receive = LoggingReceive {

    // from event stream
    case e: UpdateAction => handleUpdateAction(e)

    // direct protocol
    case p: Protocol => handleProtocol(p)

    case ScanForOverlayConfigs =>
      overlayConfigs = scanForOverlayConfigs().toSet

    case ScanForRuntimeConfigs =>
      val fullProfiles = scanForProfiles()
      profiles = fullProfiles.map { profile => profile.profileId -> profile }.toMap
      log.debug("Profiles (after scan): {}", profiles)

    case PublishServiceInfo =>
      log.debug("About to gather service infos")

      val props = Map(
        "profile.active" -> findActiveConfig().map(lrc => s"${lrc.runtimeConfig.name}-${lrc.runtimeConfig.version}").getOrElse(""),
        "profiles.valid" -> profiles.collect {
          case (ProfileId(name, version, overlays), Profile(config, localOverlays, Profile.Staged)) => s"$name-$version"
        }.mkString(","),
        "profiles.invalid" -> profiles.collect {
          case (ProfileId(name, version, overlays), Profile(config, localOverlays, Profile.Invalid(_))) => s"$name-$version"
        }.mkString(","),
        "profiles.pending" -> profiles.collect {
          case (ProfileId(name, version, overlays), Profile(config, localOverlays, Profile.Pending(_))) => s"$name-$version"
        }.mkString(",")
      ).filter { case (k, v) => !v.isEmpty() }

      val serviceInfo = ServiceInfo(
        name = context.self.path.toString,
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
  /**
   * Request lists of runtime configurations. Replied with [RuntimeConfigs].
   * FIXME: rename to GetProfiles
   */
  final case class GetRuntimeConfigs(override val requestId: String) extends Protocol
  final case class AddRuntimeConfig(override val requestId: String, runtimeConfig: RuntimeConfig) extends Protocol

  final case class AddOverlayConfig(override val requestId: String, overlayConfig: OverlayConfig) extends Protocol

  // explicit trigger staging of a config, but idea is to automatically stage not already staged configs when idle
  final case class StageProfile(override val requestId: String, name: String, version: String, overlays: Set[OverlayRef]) extends Protocol

  /**
   * Supported Replies by the [[Updater]] actor.
   */
  sealed trait Reply

  final case class RuntimeConfigs(
    requestId: String,
    staged: Seq[LocalRuntimeConfig],
    pending: Seq[LocalRuntimeConfig],
    invalid: Seq[LocalRuntimeConfig])
      extends Reply {
    override def toString(): String = s"${getClass().getSimpleName()}(requestId=${requestId},staged=${staged},pending=${pending},invalid=${invalid})"
  }

  final case class OperationSucceeded(requestId: String) extends Reply
  final case class OperationFailed(requestId: String, reason: String) extends Reply

  /**
   * Scans the profile directory for existing runtime configurations and replaces the internal state of this actor with the result.
   */
  final case object ScanForRuntimeConfigs

  /**
   * Scan the overlays directory for existing overlay configurations and replaces the internal state  of this actor with the found result.
   */
  final case object ScanForOverlayConfigs

  final case class StageNextRuntimeConfig(override val requestId: String) extends Protocol

  final case class ActivateRuntimeConfig(override val requestId: String, name: String, version: String, overlays: Set[OverlayRef]) extends Protocol

  final case class GetProgress(override val requestId: String) extends Protocol
  final case class Progress(requestId: String, progress: Int) extends Reply

  final case class UnknownRuntimeConfig(requestId: String) extends Reply
  final case class UnknownRequestId(requestId: String) extends Reply

  def props(
    baseDir: File,
    profileUpdater: (String, String) => Boolean,
    restartFramework: () => Unit,
    config: UpdaterConfig,
    launchedProfileDir: File = null): Props = {

    Props(new Updater(
      installBaseDir = baseDir,
      profileUpdater = profileUpdater,
      restartFramework = restartFramework,
      config,
      Option(launchedProfileDir)
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
      artifactsToDownload: Seq[ArtifactInProgress],
      pendingArtifactsToUnpack: Seq[ArtifactInProgress],
      artifactsToUnpack: Seq[ArtifactInProgress],
      overlays: LocalOverlays,
      issues: Seq[String]) {

    val profileId = ProfileId(config.runtimeConfig.name, config.runtimeConfig.version, overlays.overlayRefs)

    def progress(): Int = {
      val allBundlesSize = config.runtimeConfig.bundles.size
      if (allBundlesSize > 0)
        (100 / allBundlesSize) * (allBundlesSize - artifactsToDownload.size)
      else 100
    }

  }

  case class ProfileId(name: String, version: String, overlays: Set[OverlayRef]) {
    override def toString(): String =
      s"${name}-${version}_" + {
        if (overlays.isEmpty) "base"
        else overlays.toList.sorted.mkString("_")
      }
  }

  object Profile {
    sealed trait ProfileState
    final case class Pending(issues: Seq[String]) extends ProfileState
    final case class Invalid(issues: Seq[String]) extends ProfileState
    final case object Resolved extends ProfileState
    final case object Staged extends ProfileState
  }

  case class Profile(config: LocalRuntimeConfig, overlays: LocalOverlays, state: Profile.ProfileState) {
    def profileId: ProfileId = ProfileId(config.runtimeConfig.name, config.runtimeConfig.version, overlays.overlayRefs)
    def runtimeConfig: RuntimeConfig = config.resolvedRuntimeConfig.runtimeConfig
    def bundles: Seq[BundleConfig] = config.resolvedRuntimeConfig.allBundles
  }

}
