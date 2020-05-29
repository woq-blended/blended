package blended.launcher

import java.io.{ByteArrayOutputStream, File, FileOutputStream, PrintStream, PrintWriter, StringWriter}
import java.net.URLClassLoader
import java.nio.file.{Files, Paths}
import java.util.{Hashtable, Properties, ServiceLoader, UUID}

import blended.launcher.config.LauncherConfig
import blended.util.arm.ARM
import blended.updater.config._
import blended.util.logging.Logger
import com.typesafe.config.{ConfigFactory, ConfigParseOptions}
import de.tototec.cmdoption.{CmdlineParser, CmdlineParserException}
import org.osgi.framework.launch.{Framework, FrameworkFactory}
import org.osgi.framework.startlevel.{BundleStartLevel, FrameworkStartLevel}
import org.osgi.framework.wiring.FrameworkWiring
import org.osgi.framework.{Bundle, BundleContext, Constants, FrameworkEvent, FrameworkListener}
import scala.jdk.CollectionConverters._
import scala.collection.immutable.Map
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object Launcher {

  private lazy val log = Logger[Launcher.type]

  private lazy val blendedHomeDir = Option(System.getProperty("blended.home")).getOrElse(".")
  private lazy val containerConfigDirectory = blendedHomeDir + "/etc"

  private val containerIdFile : String = "blended.container.context.id"
  private val extraStartBundle : String = "blended.laucher.startbundles"

  val extraStartBundles : List[String] = Option(System.getProperty(Launcher.extraStartBundle)) match {
    case None    => List.empty
    case Some(s) => s.split(",").toList
  }

  case class InstalledBundle(jarBundle : LauncherConfig.BundleConfig, bundle : Bundle)

  /**
   * Entry point of the launcher application.
   *
   * This methods will explicitly exit the VM!
   */
  def main(args : Array[String]) : Unit = {
    try {
      run(args)
    } catch {
      case t : LauncherException =>
        log.debug(t)(s"Caught a LauncherException. Exiting with error code: ${t.errorCode} and message: ${t.getMessage()}")
        if (!t.getMessage().isEmpty()) {
          Console.err.println(s"${t.getMessage()}")
        }
        sys.exit(t.errorCode)
      case t : Throwable =>
        log.error(t)("Caught an exception. Exiting with error code: 1")
        Console.err.println(s"Error: ${t.getMessage()}")
        sys.exit(1)
    }
    sys.exit(0)
  }

  private[this] def reportError(msg : String) : Unit = {
    log.error(msg)
    Console.err.println(msg)
    sys.error(msg)
  }

  private[this] def parseArgs(args : Array[String]) : Try[Cmdline] = Try {
    val cmdline = new Cmdline()
    val cp = new CmdlineParser(cmdline)
    try {
      cp.parse(args : _*)
    } catch {
      case e : CmdlineParserException =>
        reportError(s"${e.getMessage()}\nRun launcher --help for help.")
    }

    if (cmdline.help) {
      val bos = new ByteArrayOutputStream()
      val os = new PrintStream(bos)
      cp.usage(new PrintStream(os))
      throw new LauncherException(bos.toString(), None, 0)
    }

    cmdline
  }

  private[this] def containerId(f : File, createContainerID : Boolean, onlyIfMissing : Boolean) : Try[String] = {

    val idFile = new File(containerConfigDirectory, containerIdFile)

    if (idFile.exists() && idFile.isDirectory) {
      val msg = s"The file [${idFile.getAbsoluteFile}] exists and is a directory"
      log.error(msg)
      Console.err.println(msg)
      sys.error(msg)
    }

    val generateId = createContainerID && (!onlyIfMissing || !idFile.exists())

    if (generateId && idFile.exists() && !idFile.canWrite()) {
      reportError(s"Container Id File [${idFile.getAbsolutePath}] is not writable")
    }

    if (generateId && idFile.exists()) idFile.delete()

    if (generateId) {
      log.info("Creating new container id")
      val uuid : CharSequence = UUID.randomUUID().toString.toCharArray
      Files.write(idFile.toPath, Seq(uuid).asJava)
    }

    Try {
      val lines = Files.readAllLines(Paths.get(idFile.getAbsolutePath))
      if (!lines.isEmpty) lines.get(0) else sys.error("Empty container ID file")
    }
  }

  private[this] def createAndPrepareLaunch(configs : Configs, createContainerId : Boolean, onlyIfMissing : Boolean) : Launcher = {

    val launcher = new Launcher(configs.launcherConfig)

    val errors = configs.profileConfig match {
      case Some(localConfig) =>
        // Expose the List of mandatory container properties as a System Property
        // This will be evaluated by the Container Identifier Service
        val propNames = localConfig.resolvedRuntimeConfig.runtimeConfig.properties.getOrElse(RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS, "")
        System.setProperty(RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS, propNames)

        localConfig.validate(
          includeResourceArchives = false,
          explodedResourceArchives = true
        )

      case None =>
        // if no RuntimeConfig, just check existence of bundles
        launcher.validate()
    }

    if (errors.nonEmpty) sys.error("Could not start the OSGi Framework. Details:\n" + errors.mkString("\n"))

    containerId(new File(containerConfigDirectory, containerIdFile), createContainerId, onlyIfMissing) match {
      case Failure(e) =>
        val msg = "Launcher is unable to determine the container id."
        configs.profileConfig match {
          case Some(_) =>
            // Profile mode, this is an error
            log.error(e)(msg)
            Console.err.println(msg)
            sys.error(msg)
          case None =>
            // simple config mode, this is not an error
            log.warn(e)(msg)
        }
      case Success(id) => log.info(s"ContainerId is [$id] ")
    }

    launcher
  }

  /**
   * Use this method instead of `main` if you do not want to exit the VM
   * and instead get an [LauncherException] in case of a error.
   *
   * @throws LauncherException
   */
  def run(args : Array[String]) : Unit = {
    val cmdline = parseArgs(args).get
    val handleFrameworkRestart = cmdline.handleFrameworkRestart
    var firstStart = true
    var retVal : Int = 0

    do {
      val configs = try {
        readConfigs(cmdline)
      } catch {
        case e : Throwable =>
          log.error(e)("Could not read configs")
          throw e
      }
      log.debug(s"Configs: $configs")

      cmdline.writeSystemProperties match {
        case Some(propFile) =>
          log.info("Running with --write-system-properties. About to generate properties file and exit")
          val fileProps = new Properties()
          configs.launcherConfig.systemProperties.foreach { case (k, v) => fileProps.setProperty(k, v) }
          try {
            ARM.using(new FileOutputStream(propFile)) { stream =>
              fileProps.store(stream, "Generated by Launcher")
              log.info(s"Wrote system properties file: $propFile")
            }
            retVal = 0
          } catch {
            case e : Throwable =>
              log.error(e)(s"Could not write system properties file: $propFile")
              retVal = 1
          }
        case None =>
          val createContainerId = firstStart && (cmdline.resetContainerId || cmdline.initContainerId)
          val launcher = createAndPrepareLaunch(configs, createContainerId, cmdline.initContainerId)
          retVal = launcher.run(cmdline)
          firstStart = false
      }
    } while (handleFrameworkRestart && retVal == 2)

    if (retVal != 0) throw new LauncherException("", errorCode = retVal)
  }

  case class Configs(launcherConfig : LauncherConfig, profileConfig : Option[LocalRuntimeConfig] = None)

  /**
   * Parse the command line and wrap the result into a [[Configs]] object.
   */
  def readConfigs(cmdline : Cmdline) : Configs = {
    cmdline.configFile match {
      case Some(configFile) =>
        log.info(s"About to read configFile: [$configFile]")
        val config = ConfigFactory.parseFile(new File(configFile), ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
        Configs(LauncherConfig.read(config))
      case None =>
        val profileLookup : Option[ProfileLookup] = cmdline.profileLookup.map { pl =>
          log.info(s"About to read profile lookup file: [$pl]")
          val c = ConfigFactory.parseFile(new File(pl), ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
          ProfileLookup.read(c).map { pl =>
            pl.copy(profileBaseDir = pl.profileBaseDir.getAbsoluteFile())
          }.get
        }

        val profile : String = profileLookup match {
          case Some(pl) =>
            pl.materializedDir.getPath()
          case None =>
            cmdline.profileDir match {
              case Some(profile) => profile
              case None =>
                sys.error("Either a config file or a profile dir or file or a profile lookup path must be given")
            }
        }

        val (profileDir, profileFile) = if (new File(profile).isDirectory()) {
          profile -> new File(profile, "profile.conf")
        } else {
          Option(new File(profile).getParent()).getOrElse(".") -> new File(profile)
        }

        log.info(s"Using profile directory : [$profileDir]")
        log.info(s"Using profile file      : [${profileFile.getAbsolutePath}]")

        val config = ConfigFactory.parseFile(profileFile, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()

        val runtimeConfig = ResolvedRuntimeConfig(RuntimeConfigCompanion.read(config).get)
        val launchConfig = ConfigConverter.runtimeConfigToLauncherConfig(runtimeConfig, profileDir)

        var brandingProps = Map(
          RuntimeConfig.Properties.PROFILE_DIR -> profileDir
        )
        var overlayProps = Map[String, String]()

        profileLookup.foreach { pl =>
          brandingProps ++= Map(
            RuntimeConfig.Properties.PROFILE_LOOKUP_FILE -> new File(cmdline.profileLookup.get).getAbsolutePath(),
            RuntimeConfig.Properties.PROFILES_BASE_DIR -> pl.profileBaseDir.getAbsolutePath(),
            RuntimeConfig.Properties.OVERLAYS -> pl.overlays.map(or => s"${or.name}:${or.version}").mkString(",")
          )

          val knownOverlays = LocalOverlays.findLocalOverlays(new File(profileDir).getAbsoluteFile())
          knownOverlays.find(ko => ko.overlayRefs.toSet == pl.overlays.toSet) match {
            case None =>
              if (pl.overlays.nonEmpty) {
                sys.error("Cannot find specified overlay set: " + pl.overlays.toSeq.sorted.mkString(", "))
              } else {
                log.error("Cannot find the empty overlay set (aka 'base.conf'). To be compatible with older version, we continue here as no real information is missing")
              }
            case Some(localOverlays) =>
              val newOverlayProps = localOverlays.properties
              log.debug("Found overlay provided properties: " + newOverlayProps)
              overlayProps ++= newOverlayProps
          }
        }

        Configs(
          launcherConfig = launchConfig.copy(
            branding = launchConfig.branding ++ brandingProps,
            systemProperties =
              SystemPropertyResolver.resolve((launchConfig.systemProperties ++ overlayProps) + ("blended.container.home" -> profileDir))
          ),
          profileConfig = Some(LocalRuntimeConfig(runtimeConfig, new File(profileDir)))
        )
    }
  }

  def apply(configFile : File) : Launcher = new Launcher(LauncherConfig.read(configFile))

  class RunningFramework(val framework : Framework) {

    def awaitFrameworkStop(framwork : Framework) : Int = {
      val event = framework.waitForStop(0)
      event.getType match {
        case FrameworkEvent.ERROR =>
          log.info(event.getThrowable())("Framework has encountered an error: ")
          1
        case FrameworkEvent.STOPPED =>
          log.info("Framework has been stopped by bundle " + event.getBundle)
          0
        case FrameworkEvent.STOPPED_UPDATE =>
          log.info("Framework has been updated by " + event.getBundle + " and need a restart")
          2
        case _ =>
          log.info("Framework stopped. Reason: " + event.getType + " from bundle " + event.getBundle)
          0
      }
    }

    val shutdownHook : Thread = new Thread("framework-shutdown-hook") {
      override def run() : Unit = {
        log.info("Caught kill signal: stopping framework")
        framework.stop()
        awaitFrameworkStop(framework)
        BrandingProperties.setLastBrandingProperties(new Properties())
      }
    }

    Runtime.getRuntime.addShutdownHook(shutdownHook)

    def waitForStop() : Int = {
      try {
        awaitFrameworkStop(framework)
      } catch {
        case NonFatal(x) =>
          log.error(x)(s"Framework was interrupted. Cause: [${x.getMessage()}]")
          1
      } finally {
        BrandingProperties.setLastBrandingProperties(new Properties())
        Try {
          Runtime.getRuntime.removeShutdownHook(shutdownHook)
        }
      }
    }
  }

}

class Launcher private (config : LauncherConfig) {

  import Launcher._

  private[this] val log = Logger[Launcher]

  /**
   * Validate this Launcher's configuration and return the issues if any found.
   */
  def validate() : Seq[String] = {
    val files = ("Framework JAR", config.frameworkJar) ::
      config.bundles.toList.map(b => "Bundle JAR" -> b.location)

    files.flatMap {
      case (kind, file) =>
        val f = new File(file).getAbsoluteFile()
        if (!f.exists()) {
          Some(s"$kind $f does not exists")
        } else if (!f.isFile()) {
          Some(s"$kind $f is not a file")
        } else if (!f.canRead()) {
          Some(s"$kind $f is not readable")
        } else {
          None
        }
    }
  }

  private def startFramework() : Try[Framework] = Try {
    log.info(s"Starting OSGi framework based on config: ${config}")

    // Try to locate and load the OSGi framework factory
    val frameworkURL = new File(config.frameworkJar).getAbsoluteFile.toURI().normalize().toURL()
    log.info("Framework Bundle from: " + frameworkURL)
    if (!new File(frameworkURL.getFile()).exists) throw new RuntimeException("Framework Bundle does not exist")
    val cl = new URLClassLoader(Array(frameworkURL), getClass.getClassLoader)
    log.debug("About to load FrameworkFactory")
    val frameworkFactory = ServiceLoader.load(classOf[FrameworkFactory], cl).iterator().next()
    log.debug("Loaded framework factory: " + frameworkFactory)

    // Statically export branding properties
    val brandingProps = {
      val brandingProps = new Properties()
      config.branding.foreach { case (k, v) => brandingProps.setProperty(k, v) }
      BrandingProperties.setLastBrandingProperties(brandingProps)
      log.debug("Exposing branding via class " + classOf[BrandingProperties].getName() + ": " + brandingProps)
      brandingProps
    }

    // Set system properties found in config
    config.systemProperties foreach { p =>
      log.info(s"Setting System property [${p._1}] to [${p._2}]")
      System.setProperty(p._1, p._2)
    }

    log.info("About to create framework instance...")
    val framework = frameworkFactory.newFramework(config.frameworkProperties.asJava)
    log.debug("Framework created: " + framework)

    log.debug("About to adapt framework to FrameworkStartLevel")
    val frameworkStartLevel = framework.adapt(classOf[FrameworkStartLevel])
    frameworkStartLevel.setInitialBundleStartLevel(config.defaultStartLevel)

    log.debug("About to start framework")
    framework.start()
    log.info(s"Framework started. State: ${framework.getState}")

    {
      val props = new Hashtable[String, AnyRef]()
      props.put("blended.launcher", "true")
      framework.getBundleContext.registerService(classOf[Properties], brandingProps, props)
    }

    framework
  }

  private def installBundles(context : BundleContext) : Try[Seq[Launcher.InstalledBundle]] = Try {
    log.info(s"Installing [${config.bundles.size}] bundles")
    config.bundles.map { b =>
      log.info(s"Installing Bundle: ${b}")
      // TODO: What happens here, if the JAR is not a bundle?
      val osgiBundle = context.installBundle(new File(b.location).getAbsoluteFile.toURI().normalize().toString())
      log.info("Bundle installed: " + b)
      val bundleStartLevel = osgiBundle.adapt(classOf[BundleStartLevel])
      log.debug(s"Setting start level for bundle ${osgiBundle.getSymbolicName()} to ${b.startLevel}")
      bundleStartLevel.setStartLevel(b.startLevel)
      InstalledBundle(b, osgiBundle)
    }
  }

  private val isFragment : InstalledBundle => Boolean = b =>
    Option(b.bundle.getHeaders.get(Constants.FRAGMENT_HOST)).isDefined

  private def resolveBundles(framework : Framework, bundles : Seq[InstalledBundle]) : Try[Seq[InstalledBundle]] = Try {

    val frameworkWiring : FrameworkWiring = framework.adapt(classOf[FrameworkWiring])
    frameworkWiring.resolveBundles(null /* all bundles */) match {
      case true =>
        log.info("All bundles resolved successfully")
        Seq.empty
      case false =>
        val unresolved : Seq[InstalledBundle] = bundles.filter{ b =>
          b.bundle.getState() == Bundle.INSTALLED && !isFragment(b)
        }

        val msg : String = s"The following bundles could not be resolved : ${
          unresolved.map(
            b => s"${b.bundle.getSymbolicName}-${b.bundle.getVersion}"
          ).mkString("\n", "\n", "")
        }"

        System.err.println(msg)
        log.warn(msg)

        unresolved
    }
  }

  def moveToStartlevel(framework: Framework, startLevel : Int, bundles : Seq[InstalledBundle]) : Try[Seq[(InstalledBundle, Option[Throwable])]] = Try {
    val frameworkStartLevel : FrameworkStartLevel = framework.adapt(classOf[FrameworkStartLevel])
    log.info(s"------ Entering start level [$startLevel] ------")
    frameworkStartLevel.setStartLevel(startLevel, new FrameworkListener() {
      override def frameworkEvent(event : FrameworkEvent) : Unit = {
        log.debug(s"Active start level [${startLevel}] reached")
      }
    })

    val bundlesToStart : Seq[InstalledBundle] = bundles.filter { b =>
      !isFragment(b) &&
        b.jarBundle.startLevel == startLevel &&
        (b.jarBundle.start || extraStartBundles.contains(b.bundle.getSymbolicName()))
    }

    log.info(s"Starting [${bundlesToStart.size}] bundles : [${bundlesToStart.map(_.bundle.getSymbolicName()).mkString(",")}]")

    val attemptedBundles : Seq[(InstalledBundle, Option[Throwable])] = bundlesToStart.map { bundle =>

      log.debug(s"Trying to start bundle : [${bundle.bundle.getSymbolicName()}-${bundle.bundle.getVersion()}]")
      Try {
        bundle.bundle.start()
      } match {
        case Success(_) =>
          (bundle, None)
        case Failure(t) =>
          val sw = new StringWriter()
          t.printStackTrace(new PrintWriter(sw))
          log.error("\n" + sw.getBuffer().toString() + "\n")
          (bundle, Some(t))
      }
    }

    log.info(s"Started [${attemptedBundles.count(_._2.isEmpty)}] bundles for start level [$startLevel]")

    val failedBundles : Seq[(InstalledBundle, Option[Throwable])] = attemptedBundles.filter(_._2.isDefined)
    if (failedBundles.nonEmpty) {
      log.warn(s"Could not start some bundles:\n${
        failedBundles.map(failed => s"\n - ${failed._1}\n ---> ${failed._2}")
      }")
    }

    failedBundles
  }

  /**
   * Run an (embedded) OSGiFramework based of this Launcher's configuration.
   */
  def start(cmdLine : Cmdline) : Try[Framework] = Try {

    val framework : Framework = startFramework().get
    val osgiBundles : Seq[InstalledBundle] = installBundles(framework.getBundleContext()).get
    val frameworkStartLevel : FrameworkStartLevel = framework.adapt(classOf[FrameworkStartLevel])

    /* Make sure all bundles are resolved before attempting the start sequence */
    val unresolved :Seq[InstalledBundle] = resolveBundles(framework, osgiBundles).get
    if (unresolved.nonEmpty && cmdLine.strict) {
      // in strict mode, nor resolved bundles fail the whole container
      log.error("Shutting down container due to unresolved bundles.")
      framework.stop()
    }

    // Iterate over start levels and activate bundles in the correct order
    1.to(config.startLevel).foreach { startLevel =>
      val failedBundles : Seq[(InstalledBundle, Option[Throwable])] = moveToStartlevel(framework, startLevel, osgiBundles).get

      if (failedBundles.nonEmpty && cmdLine.strict) {
        // in strict mode, bundles that failed to start fail the whole container
        log.warn("Shutting down container due to bundle start failures.")
        framework.stop()
      }
    }

    log.info("Laucher finished starting of framework and bundles. Awaiting framework termination now.")
    // Framework and bundles started

    framework
  }

  /**
   * Run an (embedded) OSGiFramework based of this Launcher's configuration.
   */
  def run(cmdLine : Cmdline) : Int = {
    start(cmdLine) match {
      case Success(framework) =>
        val handle = new RunningFramework(framework)
        if (cmdLine.test) {
          // Special test mode, we started successfully, and can now stop
          framework.stop()
        }
        handle.waitForStop()
      case Failure(e) =>
        log.error(e)("Could not start framework")
        1
    }
  }

}
