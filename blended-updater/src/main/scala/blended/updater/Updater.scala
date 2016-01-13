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
import blended.mgmt.base.StageProfile
import akka.util.Timeout
import blended.mgmt.base.ActivateProfile

object Updater {

  /**
   * Supported Messages by the [[Updater]] actor.
   */
  sealed trait Protocol {
    def requestId: String
  }
  /**
   * Supported Replies by the [[Updater]] actor.
   */
  sealed trait Reply

  /**
   * Request lists of runtime configurations. Replied with [RuntimeConfigs].
   */
  final case class GetRuntimeConfigs(override val requestId: String) extends Protocol
  final case class RuntimeConfigs(requestId: String, staged: Seq[LocalRuntimeConfig], pending: Seq[LocalRuntimeConfig], invalid: Seq[LocalRuntimeConfig]) extends Reply {
    override def toString(): String = s"${getClass().getSimpleName()}(requestId=${requestId},staged=${staged},pending=${pending},invalid=${invalid})"
  }

  final case class AddRuntimeConfig(override val requestId: String, runtimeConfig: RuntimeConfig) extends Protocol
  final case class RuntimeConfigAdded(requestId: String) extends Reply
  final case class RuntimeConfigAdditionFailed(requestId: String, reason: String) extends Reply

  final case class ScanForRuntimeConfigs(override val requestId: String) extends Protocol

  // explicit trigger staging of a config, but idea is to automatically stage not already staged configs when idle
  final case class StageRuntimeConfig(override val requestId: String, name: String, version: String) extends Protocol
  final case class StageNextRuntimeConfig(override val requestId: String) extends Protocol
  final case class RuntimeConfigStaged(requestId: String) extends Reply
  final case class RuntimeConfigStagingFailed(requestId: String, reason: String) extends Reply

  final case class ActivateRuntimeConfig(override val requestId: String, name: String, version: String) extends Protocol
  final case class RuntimeConfigActivated(requestId: String) extends Reply
  final case class RuntimeConfigActivationFailed(requestId: String, reason: String) extends Reply

  final case class GetProgress(override val requestId: String) extends Protocol
  final case class Progress(requestId: String, progress: Int) extends Reply

  final case class UnknownRuntimeConfig(requestId: String) extends Reply
  final case class UnknownRequestId(requestId: String) extends Reply

  def props(
    baseDir: File,
    profileUpdater: (String, String) => Boolean,
    restartFramework: () => Unit,
    artifactDownloaderProps: Props = null,
    artifactCheckerProps: Props = null,
    unpackerProps: Props = null,
    config: UpdaterConfig,
    launchedProfileDir: File = null): Props = {

    Props(new Updater(
      installBaseDir = baseDir,
      profileUpdater = profileUpdater,
      restartFramework = restartFramework,
      Option(artifactDownloaderProps),
      Option(artifactCheckerProps),
      Option(unpackerProps),
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
      artifactsToCheck: Seq[ArtifactInProgress],
      pendingArtifactsToUnpack: Seq[ArtifactInProgress],
      artifactsToUnpack: Seq[ArtifactInProgress],
      issues: Seq[String]) {

    val profileId = ProfileId(config.runtimeConfig.name, config.runtimeConfig.version)

    def progress(): Int = {
      val allBundlesSize = config.runtimeConfig.bundles.size
      if (allBundlesSize > 0)
        (100 / allBundlesSize) * (allBundlesSize - artifactsToDownload.size - artifactsToCheck.size)
      else 100
    }

  }

  case class ProfileId(name: String, version: String)

  object Profile {
    sealed trait ProfileState
    case class Pending(issues: Seq[String]) extends ProfileState
    case class Invalid(issues: Seq[String]) extends ProfileState
    case object Valid extends ProfileState
  }

  case class Profile(config: LocalRuntimeConfig, state: Profile.ProfileState) {
    def profileId: ProfileId = ProfileId(config.runtimeConfig.name, config.runtimeConfig.version)
    def runtimeConfig: RuntimeConfig = config.resolvedRuntimeConfig.runtimeConfig
    def bundles: Seq[BundleConfig] = config.resolvedRuntimeConfig.allBundles
  }

}

class Updater(
  installBaseDir: File,
  profileUpdater: (String, String) => Boolean,
  restartFramework: () => Unit,
  artifactDownloaderProps: Option[Props],
  artifactCheckerProps: Option[Props],
  unpackerProps: Option[Props],
  config: UpdaterConfig,
  launchedProfileDir: Option[File])
    extends Actor //    with ActorLogging 
    {
  import Updater._

  private[this] val log = LoggerFactory.getLogger(classOf[Updater])

  val artifactDownloader = context.actorOf(
    artifactDownloaderProps.getOrElse(BalancingPool(config.artifactDownloaderPoolSize).props(BlockingDownloader.props())),
    "artifactDownloader")
  val artifactChecker = context.actorOf(
    artifactCheckerProps.getOrElse(BalancingPool(config.artifactCheckerPoolSize).props(Sha1SumChecker.props())),
    "artifactChecker")
  val unpacker = context.actorOf(
    unpackerProps.getOrElse(BalancingPool(config.unpackerPoolSize).props(Unpacker.props())),
    "unpacker")

  /////////////////////
  // MUTABLE
  // requestId -> State
  private[this] var stagingInProgress: Map[String, State] = Map()

  private[this] var profiles: Map[ProfileId, Profile] = Map()

  private[this] var tickers: Seq[Cancellable] = Nil
  ////////////////////

  private[this] def stageInProgress(state: State): Unit = {
    val id = state.requestId
    val config = state.config
    val progress = state.progress()
    log.debug("Progress: {} for reqestId: {}", progress, id)

    if (state.artifactsToCheck.isEmpty && state.artifactsToDownload.isEmpty && state.issues.isEmpty && !state.pendingArtifactsToUnpack.isEmpty) {
      // start unpacking
      state.pendingArtifactsToUnpack.foreach { a =>
        unpacker ! Unpacker.Unpack(a.reqId, self, a.file, config.baseDir)
      }
      stageInProgress(state.copy(
        artifactsToUnpack = state.pendingArtifactsToUnpack,
        pendingArtifactsToUnpack = Nil
      ))
    } else if (state.artifactsToCheck.isEmpty && state.artifactsToDownload.isEmpty && state.artifactsToUnpack.isEmpty) {

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
        case Seq() => Profile.Valid -> RuntimeConfigStaged(id)
        case issues => Profile.Invalid(issues) -> RuntimeConfigStagingFailed(id, issues.mkString("; "))
      }
      profiles += state.profileId -> Profile(state.config, profileState)
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
      profile.state == Profile.Valid
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
    self ! ScanForRuntimeConfigs(UUID.randomUUID().toString())

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

  def event(event: UpdateAction): Unit = event match {
    case StageProfile(runtimeConfig) =>
      log.debug("Received stage profile request (via event stream) for {}-{}",
        Array(runtimeConfig.name, runtimeConfig.version))

      implicit val ec = context.system.dispatcher
      val timeout = new Timeout(10, TimeUnit.MINUTES)

      self.ask(AddRuntimeConfig(nextId(), runtimeConfig))(timeout).onComplete { x =>
        log.debug("Finished stage profile request (via event stream) for {}-{} with result: {}",
          Array(runtimeConfig.name, runtimeConfig.version, x))
      }

    case ActivateProfile(name, version) =>
      log.debug("Received activate profile request (via event stream) for {}-{}",
        Array(name, version))

      implicit val ec = context.system.dispatcher
      val timeout = new Timeout(10, TimeUnit.MINUTES)

      self.ask(ActivateRuntimeConfig(nextId(), name, version))(timeout).onComplete { x =>
        log.debug("Finished activation profile request (via event stream) for {}-{} with result: {}",
          Array(name, version, x))
      }
  }

  def protocol(msg: Protocol): Unit = msg match {

    case ScanForRuntimeConfigs(reqId) =>
      log.info("Scanning for profiles in: {}", installBaseDir)

      // profileFiles
      val confs = Option(installBaseDir.listFiles).getOrElse(Array()).toList.
        flatMap { nameDir =>
          Option(nameDir.listFiles).getOrElse(Array()).toList.
            flatMap { versionDir =>
              val profileFile = new File(versionDir, "profile.conf")
              if (profileFile.exists()) Some(profileFile)
              else None
            }
        }

      log.info("Found potential profile configs : {}", confs)

      // read configs
      val foundProfiles = confs.flatMap { profileFile =>
        Try {
          val versionDir = profileFile.getParentFile()
          val version = versionDir.getName()
          val name = versionDir.getParentFile.getName()
          val config = ConfigFactory.parseFile(profileFile, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
          val resolved = ResolvedRuntimeConfig(RuntimeConfig.read(config).get)
          val runtimeConfig = resolved.runtimeConfig
          if (runtimeConfig.name == name && runtimeConfig.version == version) {
            val issues = LocalRuntimeConfig(baseDir = versionDir, resolvedRuntimeConfig = resolved).validate(
              includeResourceArchives = false,
              explodedResourceArchives = true,
              checkPropertiesFile = true)
            log.debug(s"Validation result for [${name}-${version}]: ${issues.mkString(";")}")
            val profileState = issues match {
              case Seq() => Profile.Valid
              case issues => Profile.Pending(issues)
            }
            List(Profile(LocalRuntimeConfig(resolved, versionDir), profileState))
          } else {
            log.warn(s"Profile name and version do not match directory names: ${profileFile}")
            List()
          }
        }.getOrElse {
          log.warn(s"Could not read profile file: ${profileFile}")
          List()
        }
      }

      log.info(s"Profiles: ${profiles}")

      profiles = foundProfiles.map { profile => profile.profileId -> profile }.toMap

    case GetRuntimeConfigs(reqId) =>
      val ps = profiles.values.toList
      sender() ! RuntimeConfigs(reqId,
        staged = ps.collect { case Profile(config, Profile.Valid) => config },
        pending = ps.collect { case Profile(config, Profile.Pending(_)) => config },
        invalid = ps.collect { case Profile(config, Profile.Invalid(_)) => config }
      )

    case AddRuntimeConfig(reqId, config) =>
      val id = ProfileId(config.name, config.version)
      val req = sender()
      findConfig(id) match {
        case None =>
          val dir = new File(new File(installBaseDir, config.name), config.version)
          dir.mkdirs()

          val confFile = new File(dir, "profile.conf")

          config.resolve() match {
            case Success(resolved) =>
              ConfigWriter.write(RuntimeConfig.toConfig(config), confFile, None)
              profiles += id -> Profile(LocalRuntimeConfig(resolved, dir), Profile.Pending(Seq("Never checked")))
              req ! RuntimeConfigAdded(reqId)
            case Failure(e) =>
              req ! RuntimeConfigAdditionFailed(reqId, s"Given runtime config can't be resolved: ${e.getMessage}")
          }

        case Some(`config`) =>
          req ! RuntimeConfigAdded(reqId)
        case Some(collision) =>
          req ! RuntimeConfigAdditionFailed(reqId, "A different runtime config is already present under the same coordinates")
      }

    case StageNextRuntimeConfig(reqId) =>
      if (stagingInProgress.isEmpty) {
        profiles.toIterator.collect {
          case (id, Profile(_, Profile.Pending(_))) =>
            log.info("About to auto-stage profile {}", id)
            self ! StageRuntimeConfig(nextId(), id.name, id.version)
        }.take(1)
      }

    case StageRuntimeConfig(reqId, name, version) =>
      profiles.get(ProfileId(name, version)) match {
        case None =>
          sender() ! RuntimeConfigStagingFailed(reqId, s"No such runtime configuration found: ${name} ${version}")

        case Some(Profile(config, Profile.Valid)) =>
          // already staged
          sender() ! RuntimeConfigStaged(reqId)

        case Some(Profile(config, state)) =>
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

            val (existing, missing) = artifacts.partition(a => a.file.exists())

            val missingWithId = missing.map { a =>
              val resolvedUrl = a.artifact.sha1Sum.flatMap { sha1Sum =>
                findLocalResource(a.file.getName(), sha1Sum).map(f => f.getAbsoluteFile().toURI().toString())
              }.
                getOrElse {
                  config.runtimeConfig.resolveBundleUrl(a.artifact.url).getOrElse(a.artifact.url)
                }
              artifactDownloader ! BlockingDownloader.Download(a.reqId, resolvedUrl, a.file)
              a
            }
            val existingWithId = existing.filter(_.artifact.sha1Sum.isDefined).map { a =>
              artifactChecker ! Sha1SumChecker.CheckFile(a.reqId, a.file, a.artifact.sha1Sum.get)
              a
            }

            stageInProgress(State(
              requestId = reqId,
              requestActor = reqActor,
              config = config,
              artifactsToDownload = missingWithId,
              artifactsToCheck = existingWithId,
              pendingArtifactsToUnpack = pendingUnpacks,
              artifactsToUnpack = Seq(),
              issues = Seq()))
          }

      }

    case ActivateRuntimeConfig(reqId, name: String, version: String) =>
      val requestingActor = sender()
      profiles.get(ProfileId(name, version)) match {
        case Some(Profile(LocalRuntimeConfig(config, dir), Profile.Valid)) =>
          // write config
          log.debug("About to activate new profile for next startup: {}-{}", Array(name, version))
          val success = profileUpdater(name, version)
          if (success) {
            requestingActor ! RuntimeConfigActivated(reqId)
            restartFramework()
          } else {
            requestingActor ! RuntimeConfigActivationFailed(reqId, "Could not update next startup profile")
          }
        case _ =>
          requestingActor ! RuntimeConfigActivationFailed(reqId, "No such staged runtime configuration found")
      }

    case GetProgress(reqId) =>
      stagingInProgress.get(reqId) match {
        case Some(state) => sender ! Progress(reqId, state.progress())
        case None => sender() ! UnknownRequestId(reqId)
      }

  }

  override def receive: Actor.Receive = LoggingReceive {

    // from event stream
    case e: UpdateAction => event(e)

    // direct protocol
    case p: Protocol => protocol(p)

    case PublishServiceInfo =>
      log.debug("About to gather service infos")

      val props = Map(
        "profile.active" -> findActiveConfig().map(lrc => s"${lrc.runtimeConfig.name}-${lrc.runtimeConfig.version}").getOrElse(""),
        "profiles.valid" -> profiles.collect { case (ProfileId(name, version), Profile(config, Profile.Valid)) => s"$name-$version" }.mkString(","),
        "profiles.invalid" -> profiles.collect { case (ProfileId(name, version), Profile(config, Profile.Invalid(_))) => s"$name-$version" }.mkString(","),
        "profiles.pending" -> profiles.collect { case (ProfileId(name, version), Profile(config, Profile.Pending(_))) => s"$name-$version" }.mkString(",")
      ).filter { case (k, v) => !v.isEmpty() }

      val serviceInfo = ServiceInfo(
        name = context.self.path.toString,
        timestampMsec = System.currentTimeMillis(),
        lifetimeMsec = config.serviceInfoLifetimeMSec,
        props = props
      )
      log.debug("About to publish service info: {}", serviceInfo)
      context.system.eventStream.publish(serviceInfo)

    case msg: BlockingDownloader.DownloadReply =>
      val foundProgress = stagingInProgress.values.flatMap { state =>
        state.artifactsToDownload.find { bip => bip.reqId == msg.reqId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown download id {}", msg.reqId)
        case (state, bundleInProgress) :: _ =>
          val artifactsToDownload = state.artifactsToDownload.filter(bundleInProgress != _)
          msg match {
            case BlockingDownloader.DownloadFinished(_, url, file) =>
              val toCheck = bundleInProgress.artifact.sha1Sum.map { sha1Sum =>
                // only check if we have a checksum
                val newToCheck = bundleInProgress.copy(reqId = nextId())
                artifactChecker ! Sha1SumChecker.CheckFile(newToCheck.reqId, newToCheck.file, sha1Sum)
                newToCheck
              }
              stageInProgress(state.copy(
                artifactsToDownload = artifactsToDownload,
                artifactsToCheck = toCheck.toList ++: state.artifactsToCheck
              ))
            case BlockingDownloader.DownloadFailed(_, url, file, error) =>
              stageInProgress(state.copy(
                artifactsToDownload = artifactsToDownload,
                issues = s"Download failed for ${file} (${error.getClass().getSimpleName()}: ${error.getMessage()})" +: state.issues
              ))
          }
      }

    case msg: Sha1SumChecker.Reply =>
      val foundProgress = stagingInProgress.values.flatMap { state =>
        state.artifactsToCheck.find { bip => bip.reqId == msg.reqId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown check id {}", msg.reqId)
        case (state, bundleInProgress) :: _ =>
          val artifactsToCheck = state.artifactsToCheck.filter(bundleInProgress != _)
          msg match {
            case Sha1SumChecker.ValidChecksum(_, _, _) =>
              stageInProgress(state.copy(
                artifactsToCheck = artifactsToCheck
              ))
            case Sha1SumChecker.InvalidChecksum(_, file, sha1Sum) =>
              stageInProgress(state.copy(
                artifactsToCheck = artifactsToCheck,
                issues = s"Invalid checksum for file ${file} (expected: ${bundleInProgress.artifact.sha1Sum}, found: ${sha1Sum})" +: state.issues
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
