import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

import coursier.Repository
import coursier.maven.MavenRepository
import mill.api.{Ctx, Loose, Result}
import mill.{PathRef, _}
import mill.define.{Command, ExternalModule, Input, Sources, Target, Task, Worker}
import mill.scalajslib.ScalaJSModule
import mill.scalajslib.api.ModuleKind
import mill.scalalib._
import mill.scalalib.api.CompilationResult
import mill.scalalib.publish._
import os.Path
import sbt.testing.{Fingerprint, Framework}
import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`
import mill.contrib.scoverage.api.ScoverageReportWorkerApi.ReportType
import mill.contrib.scoverage.{ScoverageModule, ScoverageReportWorker}
import mill.eval.Evaluator
import mill.main.RunScript
import mill.modules.Jvm

// This import the mill-osgi plugin
import $ivy.`de.tototec::de.tobiasroeser.mill.osgi:0.3.0`
import de.tobiasroeser.mill.osgi._

import $file.build_util
import build_util.{FilterUtil, ScoverageReport, ZipUtil, GitModule}

import $file.build_deps
import build_deps.Deps

/** Project directory. */
val baseDir: os.Path = build.millSourcePath

def blendedVersion = T.input {
  T.env.get("CI") match {
    case Some(ci @ ("1" | "true")) =>
      val version = GitSupport.publishVersion()._2
      T.log.info(s"Using git-based version: ${version} (CI=${ci})")
      version
    case _ => os.read(baseDir / "version.txt").trim()
  }
}

object GitSupport extends GitModule {
  override def millSourcePath: Path = baseDir
}

/** Configure additional repositories. */
trait BlendedCoursierModule extends CoursierModule {
  private def zincWorker: ZincWorkerModule = mill.scalalib.ZincWorkerModule
  override def repositories: Seq[Repository] = zincWorker.repositories ++ Seq(
    MavenRepository("https://repo.spring.io/libs-release")
  )
}

/** Configure plublish settings. */
trait BlendedPublishModule extends PublishModule {
  def description: String = "Blended module ${blendedModule}"
  override def publishVersion = T { blendedVersion() }
  override def pomSettings: T[PomSettings] = T {
    PomSettings(
      description = description,
      organization = "de.wayofquality.blended",
      url = "https://github.com/woq-blended",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("woq-blended", "blended"),
      developers = Seq(
        Developer("atooni", "Andreas Gies", "https://github.com/atooni"),
        Developer("lefou", "Tobias Roeser", "https://github.com/lefou")
      )
    )
  }
}

trait BlendedBaseModule extends SbtModule with BlendedCoursierModule with BlendedPublishModule with OsgiBundleModule with ScoverageModule { blendedModuleBase =>
  def deps: Deps
  /** The blended module name. */
  def blendedModule: String = millModuleSegments.parts.mkString(".")
  override def artifactName: T[String] = blendedModule
  def scalaVersion = T{ deps.scalaVersion }
  def scalaBinVersion = T { scalaVersion().split("[.]").take(2).mkString(".") }

  def exportPackages: Seq[String] = Seq(blendedModule)
  def essentialImportPackage: Seq[String] = Seq()
  override def osgiHeaders: T[OsgiHeaders] = T{
    super.osgiHeaders().copy(
      `Export-Package` = exportPackages,
      `Import-Package` =
        // scala compatible binary version control
        Seq(s"""scala.*;version="[${scalaBinVersion()}.0,${scalaBinVersion()}.50]"""") ++
          essentialImportPackage ++
          Seq("*")
    )
  }

  override def millSourcePath: os.Path = baseDir / blendedModule
  override def resources = T.sources { super.resources() ++ Seq(
    PathRef(millSourcePath / 'src / 'main / 'binaryResources)
  )}
  override def bundleSymbolicName = blendedModule

  override def scalacOptions = Seq("-deprecation", "-target:jvm-1.8")

  override def scoverageVersion = deps.scoverageVersion

  def mapToScoverageModule(m: JavaModule) = m match {
    case module: ScoverageModule =>
      // instead of depending on the base module, we depend on it's inner scoverage module
      module.scoverage
    case module => module
  }

  override val scoverage: ScoverageData = new ScoverageData(implicitly)
  class ScoverageData(ctx0: mill.define.Ctx) extends super.ScoverageData(ctx0) with OsgiBundleModule {
    // we ensure, out scoverage enhancer is also a valid OSGi module to drop-in replace it in all tests
    override def bundleSymbolicName: T[String] = T{ blendedModuleBase.bundleSymbolicName() }
    override def bundleVersion: T[String] = T{ blendedModuleBase.bundleVersion() }
    override def osgiHeaders: T[OsgiHeaders] = T{ blendedModuleBase.osgiHeaders() }
    override def reproducibleBundle: T[Boolean] = T{ blendedModuleBase.reproducibleBundle() }
    override def embeddedJars: T[Seq[PathRef]] = T{ blendedModuleBase.embeddedJars() }
    override def explodedJars: T[Seq[PathRef]] = T{ blendedModuleBase.explodedJars() }
    override def moduleDeps: Seq[JavaModule] = blendedModuleBase.moduleDeps.map(mapToScoverageModule)
    override def recursiveModuleDeps: Seq[JavaModule] = blendedModuleBase.recursiveModuleDeps.map(mapToScoverageModule)
  }

  trait Tests extends super.Tests with super.ScoverageTests {
    /** Ensure we don't include the non-scoverage-enhanced classes. */
//    override def moduleDeps: Seq[JavaModule] = super.moduleDeps.filterNot(_ == blendedModuleBase)
    override def moduleDeps: Seq[JavaModule] = super.moduleDeps.map(mapToScoverageModule)
    override def recursiveModuleDeps: Seq[JavaModule] = super.recursiveModuleDeps.map(mapToScoverageModule)
    override def transitiveLocalClasspath: T[Loose.Agg[PathRef]] = T { T.traverse(recursiveModuleDeps)(m => m.jar)() }
    override def ivyDeps = T{ super.ivyDeps() ++ Agg(
      deps.scalatest
    )}
    override def testFrameworks = Seq("org.scalatest.tools.Framework")
    /** Empty, we use [[testResources]] instead to model sbt behavior. */
    override def runIvyDeps: Target[Loose.Agg[Dep]] = T{ super.runIvyDeps() ++ Agg(
      deps.logbackClassic,
      deps.jclOverSlf4j
    )}
    override def resources = T.sources { Seq.empty[PathRef] }

    def testResources: Sources = T.sources(
      millSourcePath / "src" / "test" / "resources",
      millSourcePath / "src" / "test" / "binaryResources"
    )
    // TODO: set projectTestOutput property to resources directory
    /** Used by all tests, e.g. for logback config. */
    def logResources = T {
      val moduleSpec = toString()
      val dest = T.ctx().dest
      val logConfig =
        s"""<configuration>
           |
           |  <appender name="FILE" class="ch.qos.logback.core.FileAppender">
           |    <file>${baseDir.toString()}/out/testlog-${scalaBinVersion()}/test-${moduleSpec}.log</file>
           |
           |    <encoder>
           |      <pattern>%d{yyyy-MM-dd-HH:mm.ss.SSS} | %8.8r | %-5level [%t] %logger : %msg%n</pattern>
           |    </encoder>
           |  </appender>
           |
           |  <logger name="blended" level="debug" />
           |  <logger name="domino" level="debug" />
           |  <logger name="App" level="debug" />
           |
           |  <root level="INFO">
           |    <appender-ref ref="FILE" />
           |  </root>
           |
           |</configuration>
           |""".stripMargin
      os.write(dest / "logback-test.xml", logConfig)
      PathRef(dest)
    }
    /** A T.input, because this needs to run always to be always fresh, as we intend to write into that dir when executing tests.
     * This is in migration from sbt-like setup.
     */
    def copiedResources: T[PathRef] = T.input {
      val dest = T.dest
      testResources().foreach { p =>
        if(os.exists(p.path)) {
          if(os.isDir(p.path)) {
            os.list(p.path).foreach { p1 =>
              os.copy.into(p1, dest)
            }
          }
          else {
            os.copy.over(p.path, dest)
          }
        }
      }
      PathRef(dest)
    }
    override def runClasspath: Target[Seq[PathRef]] = T{ super.runClasspath() ++ Seq(logResources(), copiedResources()) }
    override def forkArgs: Target[Seq[String]] = T{ super.forkArgs() ++ Seq(
      s"-DprojectTestOutput=${copiedResources().path.toString()}"
    )}
  }

  val otherTestGroup = "other"
  /** A Map of groups with their belonging test suites.
   * The groups name  [[otherTestGroup]] is reserved for all tests that don't need to run in an extra JVM. */
  def testGroups: Map[String, Set[String]] = Map()
  /** Test group names, derived from [[testGroups]]. */
  def crossTestGroups: Seq[String] = (Set(otherTestGroup) ++ testGroups.keySet).toSeq

  /** A test module that only executed the tests from the configured [[ForkedTest#testGroup]]. */
  trait ForkedTest extends Tests {

    def testGroup: String = otherTestGroup

    def detectTestGroups: T[Map[String, Set[String]]] = T {
      if(testFrameworks() != Seq("org.scalatest.tools.Framework")) {
        Result.Failure("Unsupported test framework set")
      } else {
        val testClasspath = runClasspath().map(_.path)
        val cl = new URLClassLoader(testClasspath.map(_.toNIO.toUri().toURL()), getClass().getClassLoader())
        val framework: Framework = TestRunner.frameworks(testFrameworks())(cl).head
        val testClasses: Loose.Agg[(Class[_], Fingerprint)] = Lib.discoverTests(cl, framework, Agg(compile().classes.path))
        val groups: Map[String, List[(Class[_], Fingerprint)]] = testClasses.toList.groupBy { case (cl, fp) =>
          cl.getAnnotations().map{ anno =>
//            println(s"anno: ${anno}, name: ${anno.getClass().getName()}")
          }
          val isFork = cl.getAnnotations().exists(a => a.toString().contains("blended.testsupport.RequiresForkedJVM") || a.getClass().getName().contains("blended.testsupport.RequiresForkedJVM"))
          if(isFork) cl.getName()
          else otherTestGroup
        }
        val groupNames: Map[String, Set[String]] = groups.mapValues(_.map(_._1.getName()).toSet).toMap
        cl.close()
        Result.Success(groupNames)
      }
    }

    /** redirect to "other"-compile */
    override def compile: T[CompilationResult] = if(testGroup == otherTestGroup) super.compile else otherModule.compile
    /** Override this to define the target which compiles the test sources */
    def otherModule: ForkedTest

    def checkTestGroups(): Command[Unit] = T.command {
      if(testGroup == otherTestGroup) T{
        // only check in default cross instance "other"

        val anyGroupsDetected = detectTestGroups().keySet != Set(otherTestGroup)
        val anyGroupsDefined = testGroups.nonEmpty
        val relevantGroups: PartialFunction[(String, Set[String]), Set[String]] = {
          case (name, tests) if name != otherTestGroup => tests
        }
        val definedGroupedTests = testGroups.collect(relevantGroups).toSet
        val detectedGroupedTests = detectTestGroups().collect(relevantGroups).toSet

        if((anyGroupsDetected || anyGroupsDefined) && definedGroupedTests != detectedGroupedTests) {
          T.log.error(s"Test groups invalid. Detected the following explicit groups: ${detectTestGroups() - otherTestGroup}.")
        }
      } else T{
        // depend on the other check
        otherModule.checkTestGroups()()
      }
    }

    override def test(args: String*): Command[(String, Seq[TestRunner.Result])] =
      if(args.isEmpty) T.command{
        super.testTask(testCachedArgs)()
      }
      else super.test(args: _*)

    override def testCachedArgs: T[Seq[String]] = T{
      checkTestGroups()()
      val tests = if(testGroup == otherTestGroup && testGroups.get(otherTestGroup).isEmpty) {
        val allTests = detectTestGroups().values.toSet.flatten
        val groupTests = testGroups.values.toSet.flatten
        allTests -- groupTests
      } else {
        testGroups(testGroup)
      }
      T.log.debug(s"tests: ${tests}")
      if(tests.isEmpty) {
        Seq("-w", "NON_TESTS")
      } else {
        tests.toSeq.flatMap(tc => Seq("-w", tc))
      }
    }
  }
}

trait BlendedJvmModule extends BlendedBaseModule { jvmBase =>
  override def millSourcePath = super.millSourcePath / "jvm"
  override def sources = T.sources {
    super.sources() ++ Seq(PathRef(millSourcePath / os.up / 'shared / 'src / 'main / 'scala))
  }
  override def resources = T.sources { super.resources() ++ Seq(
      PathRef(millSourcePath / os.up / 'shared / 'src / 'main / 'resources),
      PathRef(millSourcePath / os.up / 'shared / 'src / 'main / 'binaryResources)
  )}

  trait Tests extends super.Tests {
    override def sources = T.sources {
      super.sources() ++ Seq(PathRef(jvmBase.millSourcePath / os.up / 'shared / 'src / 'test / 'scala))
    }
    override def testResources = T.sources { super.testResources() ++ Seq(
      PathRef(jvmBase.millSourcePath / os.up / 'shared / 'src / 'test / 'resources),
      PathRef(jvmBase.millSourcePath / os.up / 'shared / 'src / 'test / 'binaryResources)
    )}
  }

  trait Js extends ScalaJSModule with BlendedPublishModule { jsBase =>
    override def millSourcePath = jvmBase.millSourcePath / os.up / "js"
    override def scalaJSVersion = deps.scalaJsVersion
    override def scalaVersion = jvmBase.scalaVersion
    override def sources: Sources = T.sources(
      millSourcePath / "src" / "main" / "scala",
      millSourcePath / os.up / "shared" / "src" / "main" / "scala"
    )
    override def moduleKind: T[ModuleKind] = T{ ModuleKind.CommonJSModule }
    def blendedModule = jvmBase.blendedModule
    override def artifactName: T[String] = jvmBase.artifactName
    trait Tests extends super.Tests {
      override def sources: Sources = T.sources(
        jsBase.millSourcePath / "src" / "test" / "scala"
      )
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        deps.js.scalatest
      )}
      override def testFrameworks = Seq("org.scalatest.tools.Framework")
      override def moduleKind: T[ModuleKind] = T{ ModuleKind.CommonJSModule }
    }
  }
}

trait DistModule extends CoursierModule {
  def deps: Deps
  override def millSourcePath: Path = super.millSourcePath / os.up

  /** Sources to put into the dist file. */
  def sources: Sources
  /** Sources to put into the dist file after filtering */
  def filteredSources: Sources
  /** Filter properties to apply to [[filteredSources]]. */
  def filterProperties: Target[Map[String, String]]
  /** Dependencies to put under lib directory inside the dist. */
  def libIvyDeps: Target[Loose.Agg[Dep]] = T{ Agg.empty[Dep] }
  /** Dependencies to put under lib directory inside the dist. */
  def libModules: Seq[PublishModule] = Seq()
  def filterRegex: String = "[@]([^\\n]+?)[@]"

  def scalaVersion: Target[String] = T{ deps.scalaVersion }

  def expandedFilteredSources: Target[PathRef] = T{
    val dest = T.ctx().dest
    FilterUtil.filterDirs(
      unfilteredResourcesDirs = filteredSources().map(_.path),
      pattern = filterRegex,
      filterTargetDir = dest,
      props = filterProperties(),
      failOnMiss = true
    )
    PathRef(dest)
  }

  def transitiveLibModules: Seq[PublishModule] = libModules.flatMap(_.transitiveModuleDeps).collect{ case m: PublishModule => m }.distinct
  /**
   * The transitive ivy dependencies of this module and all it's upstream modules
   */
  def transitiveLibIvyDeps: T[Agg[Dep]] = T{
    libIvyDeps() ++ T.traverse(libModules)(_.transitiveIvyDeps)().flatten
  }

  def distName: T[String] = T{"out"}

  override def resolveCoursierDependency: Task[Dep => coursier.Dependency] = T.task{
    Lib.depToDependency(_: Dep, scalaVersion(), "")
  }

  def resolvedLibs: Target[PathRef] = T{
    val dest = T.ctx().dest
    val libs = Target.traverse(transitiveLibModules)(m => T.task {
      (m.artifactId(), m.publishVersion(), m.jar().path)
    })()

    libs.foreach { case (aId, version, jar) =>
      val target = dest / "lib" / s"${aId}-${version}.jar"
      os.copy(jar, target, createFolders = true)
    }

    val jars = resolveDeps(transitiveLibIvyDeps)().map(_.path)

    jars.foreach { jar =>
      os.copy(jar, dest / "lib" / jar.last, createFolders = true)
    }
    PathRef(dest)
  }

  /** Creates the distribution zip file */
  def zip : T[PathRef] = T{
    val dirs  =
      sources().map(_.path) ++ Seq(
        expandedFilteredSources().path,
        resolvedLibs().path
      )

    Jvm.createJar(dirs)
  }
}

object blended extends Cross[BlendedCross](Deps.scalaVersions.keys.toSeq: _*)
class BlendedCross(crossScalaVersion: String) extends GenIdeaModule { blended =>
  override def skipIdea: Boolean = crossScalaVersion != Deps.Deps_2_12.scalaVersion

  // correct the unneeded cross sub-dir
  override def millSourcePath: Path = super.millSourcePath / os.up

  val deps: Deps = build_deps.Deps.scalaVersions(crossScalaVersion)
//  val Deps = deps

  trait BlendedModule extends BlendedBaseModule {
    override def deps: Deps = blended.deps
    // remove the scala version
    override def blendedModule: String = millModuleSegments.parts.filterNot(crossScalaVersion == _).mkString(".")
    override def skipIdea: Boolean = crossScalaVersion != Deps.Deps_2_12.scalaVersion
    trait Tests extends super.Tests {
      override def skipIdea: Boolean = crossScalaVersion != Deps.Deps_2_12.scalaVersion
    }
    trait ForkedTests extends super.ForkedTest {
      override def skipIdea: Boolean = crossScalaVersion != Deps.Deps_2_12.scalaVersion
    }
  }

  object activemq extends Module {
    object brokerstarter extends BlendedModule {
      override val description : String =
        """A simple wrapper around an Active MQ broker that makes sure that the broker is completely
          |started before exposing a connection factory OSGi service""".stripMargin
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.activeMqBroker,
        deps.activeMqSpring,
        deps.springBeans,
        deps.springContext,
        deps.springCore
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.akka,
        blended.jms.utils,
        blended.security.boot,
        blended.security
      )
      override def osgiHeaders = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.BrokerActivator")
      )}
      override def testGroups: Map[String, Set[String]] = Map(
        "BrokerActivatorSpec" -> Set("blended.activemq.brokerstarter.internal.BrokerActivatorSpec")
      )
      object test extends Cross[Test](crossTestGroups: _*)
      class Test(override val testGroup: String) extends ForkedTest {
        override def otherModule: ForkedTest =  brokerstarter.test(otherTestGroup)
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.activeMqKahadbStore,
          deps.springCore,
          deps.springBeans,
          deps.springContext,
          deps.springExpression
        )}
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport,
          blended.testsupport.pojosr
        )
      }
    }
    object client extends BlendedModule {
      override val description : String = "An Active MQ Connection factory as a service"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.activeMqClient
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.util,
        blended.util.logging,
        blended.jms.utils,
        blended.akka,
        blended.streams
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.AmqClientActivator")
      )}
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.activeMqBroker,
          deps.activeMqKahadbStore
        )}
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport,
          blended.testsupport.pojosr
        )
      }
    }
  }

  object akka extends BlendedModule {
    override val description = "Provide OSGi services and API to use Actors in OSGi bundles with a shared ActorSystem."
    override def ivyDeps = T{ super.ivyDeps() ++ Agg(
      deps.orgOsgi,
      deps.akkaActor,
      deps.domino
    )}

    override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
      blended.util.logging,
      blended.container.context.api,
      blended.domino
    )

    override def exportPackages : Seq[String] = super.exportPackages ++ Seq(s"$blendedModule.protocol")
    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Some(s"${blendedModule}.internal.BlendedAkkaActivator")
    )}
    object test extends Tests {
      override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
        blended.testsupport,
        blended.testsupport.pojosr
      )
    }

    object http extends BlendedModule {
      override val description : String = "Provide Akka HTTP support"
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        deps.domino,
        deps.akkaStream,
        deps.akkaOsgi,
        deps.akkaHttp
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.container.context.api,
        blended.domino,
        blended.util,
        blended.util.logging,
        blended.akka,
        blended.jmx
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.BlendedAkkaHttpActivator")
      )}
      object test extends Tests {

        override def forkArgs = T{ super.forkArgs() ++ Seq("-Dsun.net.client.defaultReadTimeout=3000")}

        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.akkaTestkit,
          deps.akkaSlf4j,
          deps.mockitoAll,
          deps.akkaHttpTestkit,
          deps.akkaStreamTestkit,
          deps.sttp,
          deps.sttpAkka
        )}
        override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
          blended.testsupport,
          blended.testsupport.pojosr
        )
      }

      object api extends BlendedModule {
        override val description : String = "Package the Akka Http API into a bundle."
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.akkaHttp,
          deps.akkaHttpCore,
          deps.akkaParsing
        )}
        override def exportPackages : Seq[String] = Seq(
          s"akka.http.*;version=${deps.akkaHttpVersion};-split-package:=merge-first"
        )
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Import-Package` = Seq(
            """scala.compat.*;version="[0.8,1)"""",
            """scala.*;version="[2.12,2.12.50]"""",
            "com.sun.*;resolution:=optional",
            "sun.*;resolution:=optional",
            "net.liftweb.*;resolution:=optional",
            "play.*;resolution:=optional",
            "twirl.*;resolution:=optional",
            "org.json4s.*;resolution:=optional",
            "*"
          ),
          `Private-Package` = Seq(
            "akka.macros.*",
            "akka.parboiled2.*",
            "akka.shapeless.*"
          )
        )}
//        override def exportContents: T[Seq[String]] = T{ Seq(
//          s"akka.http.*;version=${deps.akkaHttpVersion};-split-package:=merge-first"
//        )}
//        override def embeddedJars: T[Seq[PathRef]] = T{
//          resolveDeps(T.task{ ivyDeps().map(_.exclude("*" -> "*")) })().toSeq
//        }
      }

      object jmsqueue extends BlendedModule {
        override val description : String = "Provide a simple REST interface to consume messages from JMS Queues"
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.domino,
          deps.jms11Spec
        )}
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.domino,
          blended.container.context.api,
          blended.akka,
          blended.akka.http,
          blended.util
        )
        object test extends Tests {
          override def ivyDeps = T{ super.ivyDeps() ++ Agg(
            deps.sttp,
            deps.sttpAkka,
            deps.akkaSlf4j,
            deps.akkaTestkit,
            deps.akkaStreamTestkit,
            deps.akkaHttpTestkit,
            deps.activeMqBroker,
            deps.activeMqKahadbStore
          )}
          override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
            blended.activemq.brokerstarter,
            blended.streams,
            blended.testsupport,
            blended.testsupport.pojosr
          )
        }
      }

      object proxy extends BlendedModule {
        override val description : String = "Provide Akka HTTP Proxy support"
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.domino,
          deps.akkaStream,
          deps.akkaHttp,
          deps.akkaActor
        )}
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.domino,
          blended.container.context.api,
          blended.akka,
          blended.akka.http,
          blended.util,
          blended.util.logging
        )
        object test extends Tests {
          override def ivyDeps = T{ super.ivyDeps() ++ Agg(
            deps.akkaSlf4j,
            deps.akkaTestkit,
            deps.akkaStreamTestkit,
            deps.akkaHttpTestkit
          )}
          override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
            blended.testsupport,
            blended.testsupport.pojosr
          )
        }
      }

      object restjms extends BlendedModule {
        override val description : String = "Provide a simple REST interface to perform JMS request / reply operations"
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.domino,
          deps.akkaStream,
          deps.akkaHttp,
          deps.akkaActor,
          deps.jms11Spec
        )}
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.domino,
          blended.container.context.api,
          blended.akka,
          blended.streams,
          blended.akka.http,
          blended.util
        )

        override def testGroups: Map[String, Set[String]] = Map(
          "JMSRequestorSpec" -> Set("blended.akka.http.restjms.internal.JMSRequestorSpec"),
          "JMSChunkedRequestorSpec" -> Set("blended.akka.http.restjms.internal.JMSChunkedRequestorSpec")
        )

        object test extends Cross[Test](crossTestGroups: _*)
        class Test(override val testGroup: String) extends ForkedTest {
          override def otherModule: ForkedTest = restjms.test(otherTestGroup)
          override def ivyDeps = T{ super.ivyDeps() ++ Agg(
            deps.sttp,
            deps.sttpAkka,
            deps.activeMqBroker,
            deps.activeMqClient,
            deps.springBeans,
            deps.springContext,
            deps.akkaSlf4j,
            deps.akkaTestkit
          )}
          override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
            blended.testsupport,
            blended.activemq.brokerstarter,
            blended.testsupport.pojosr
          )
        }
      }

      object sample extends Module {
        object helloworld extends BlendedModule {
          override val description = "A sample Akka HTTP bases HTTP endpoint for the blended container"
          override def millSourcePath: Path = baseDir / "blended.samples" / blendedModule
          override def ivyDeps = T{ super.ivyDeps() ++ Agg(
            deps.domino,
            deps.orgOsgi,
            deps.orgOsgiCompendium,
            deps.slf4j
          )}
          override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
            blended.akka,
            blended.akka.http,
            blended.akka.http.api
          )
          override def osgiHeaders = T { super.osgiHeaders().copy(
            `Bundle-Activator` = Option(s"$blendedModule.internal.HelloworldActivator")
          )}
          object test extends Tests {
            override def ivyDeps = T{ super.ivyDeps() ++ Agg(
              deps.slf4jLog4j12,
              deps.akkaStreamTestkit,
              deps.akkaHttpTestkit
            )}
          }
        }
      }
    }

    object logging extends BlendedModule {
      override val description = "Redirect Akka Logging to the Blended logging framework"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.akkaActor
      )}

      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.util.logging
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Private-Package` = Seq(blendedModule),
        `Fragment-Host` = Some("com.typesafe.akka.actor")
      )}
    }
  }

  object container extends Module {

    object context extends Module {

      object api extends BlendedModule {
        override def description = "The API for the Container Context and Identifier Service"
        override def ivyDeps = Agg(
          deps.typesafeConfig
        )

        override def essentialImportPackage: Seq[String] = Seq("blended.launcher.runtime;resolution:=optional")
        override def moduleDeps = Seq(
          blended.util.logging,
          blended.security.crypto
        )
        object test extends Tests {
          override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
            blended.testsupport
          )
        }
      }

      object impl extends BlendedModule {
        override def description = "A simple OSGi service to provide access to the container's config directory"
        override def ivyDeps = Agg(
          deps.orgOsgiCompendium,
          deps.orgOsgi,
          deps.domino,
          deps.slf4j,
          deps.julToSlf4j,
          deps.springExpression,
          deps.springCore
        )
        override def moduleDeps = Seq(
          blended.security.crypto,
          blended.container.context.api,
          blended.util.logging,
          blended.util,
          blended.updater.config,
          blended.launcher
        )
        override def essentialImportPackage: Seq[String] = Seq("blended.launcher.runtime;resolution:=optional")
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Bundle-Activator` = Some(s"${blendedModule}.internal.ContainerContextActivator")
        )}
        object test extends Tests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            deps.scalacheck
          )}
          override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
            blended.testsupport
          )
        }
      }
    }
  }

  object domino extends BlendedModule {
    override def description = "Blended Domino extension for new Capsule scopes"
    override def ivyDeps = Agg(
      deps.typesafeConfig,
      deps.domino
    )
    override def moduleDeps = Seq(
      blended.util.logging,
      blended.container.context.api
    )
    object test extends Tests
  }

  object file extends BlendedModule {
    override val description : String = "Bundle to define a customizable Filedrop / Filepoll API"
    override def moduleDeps = super.moduleDeps ++ Seq(
      blended.akka,
      blended.jms.utils,
      blended.streams
    )
    object test extends Tests {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.commonsIo,
        deps.activeMqBroker,
        deps.activeMqKahadbStore,
        deps.akkaTestkit,
        deps.akkaSlf4j
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport
      )
      override def testResources: Sources = T.sources { super.testResources() ++ Seq(
        PathRef(millSourcePath / os.up / "src" / "test" / "filterResources")
      )}
    }
  }

  object hawtio extends Module {
    object login extends BlendedModule {
      override val description : String = "Adding required imports to the hawtio war bundle"
      override def essentialImportPackage: Seq[String] = Seq(
        "blended.security.boot",
        "com.sun.jndi.ldap;resolution:=optional"
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Fragment-Host` = Some("io.hawt.hawtio-web")
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.security.boot
      )
    }
  }

  object jetty extends Module {
    object boot extends BlendedModule {
      override val description : String = "Bundle wrapping the original jetty boot bundle to dynamically provide SSL Context via OSGI services"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
       deps.domino,
        deps.jettyOsgiBoot
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.domino,
        blended.util.logging
      )
      private val jettyVersion = """version="[9.4,20)""""
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Option(s"${blendedModule}.internal.JettyActivator"),
        `Import-Package` = Seq(
          s"org.eclipse.jetty.annotations;$jettyVersion;resolution:=optional",
          s"org.eclipse.jetty.deploy;$jettyVersion",
          s"org.eclipse.jetty.deploy.bindings;$jettyVersion",
          s"org.eclipse.jetty.deploy.graph;$jettyVersion",
          s"org.eclipse.jetty.http;$jettyVersion",
          s"org.eclipse.jetty.server;$jettyVersion",
          s"org.eclipse.jetty.server.handler;$jettyVersion",
          s"org.eclipse.jetty.util;$jettyVersion",
          s"org.eclipse.jetty.util.thread;$jettyVersion",
          s"org.eclipse.jetty.util.component;$jettyVersion",
          s"org.eclipse.jetty.util.log;$jettyVersion",
          s"org.eclipse.jetty.util.resource;$jettyVersion",
          s"org.eclipse.jetty.webapp;$jettyVersion",
          s"org.eclipse.jetty.xml;$jettyVersion",
          s"org.osgi.service.event",
          s"javax.mail.*;resolution:=optional",
          s"javax.transaction.*;resolution:=optional",
          s"org.objectweb.asm;resolution:=optional",
          s"org.osgi.service.cm",
          s"org.osgi.service.url",
          s"org.slf4j.*;resolution:=optional",
          s"org.xml.sax,org.xml.sax.helpers",
          "*"
        ),
        `DynamicImport-Package` = Seq(
          s"org.eclipse.jetty.*;$jettyVersion"
        ),
        `Bundle-Classpath` = Seq(".") ++ embeddedJars().map(_.path.last)
      )}
      override def embeddedJars: T[Seq[PathRef]] = T{ super.embeddedJars() ++
        resolveDeps(T.task {
          Agg(deps.jettyOsgiBoot.exclude("*" -> "*"))
        })().toSeq
      }
    }
  }

  object jms extends Module {
    object bridge extends BlendedModule {
      override val description : String = "A generic JMS bridge to connect the local JMS broker to en external JMS"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.akkaActor,
        deps.akkaStream,
        deps.typesafeConfig
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.util,
        blended.util.logging,
        blended.jms.utils,
        blended.domino,
        blended.akka,
        blended.streams
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Option(s"${blendedModule}.internal.BridgeActivator")
      )}
      override def testGroups: Map[String, Set[String]] = Map(
        "RouteAfterRetrySpec" -> Set("blended.jms.bridge.internal.RouteAfterRetrySpec"),
        "InboundRejectBridgeSpec" -> Set("blended.jms.bridge.internal.InboundRejectBridgeSpec"),
        "TransactionSendFailedRejectBridgeSpec" -> Set("blended.jms.bridge.internal.TransactionSendFailedRejectBridgeSpec"),
        "TransactionSendFailedRetryBridgeSpec" -> Set("blended.jms.bridge.internal.TransactionSendFailedRetryBridgeSpec"),
        "SendFailedRetryBridgeSpec" -> Set("blended.jms.bridge.internal.SendFailedRetryBridgeSpec"),
        "MapToExternalBridgeSpec" -> Set("blended.jms.bridge.internal.MapToExternalBridgeSpec"),
        "OutboundBridgeSpec" -> Set("blended.jms.bridge.internal.OutboundBridgeSpec"),
        "InboundBridgeTrackedSpec" -> Set("blended.jms.bridge.internal.InboundBridgeTrackedSpec"),
        "InboundBridgeUntrackedSpec" -> Set("blended.jms.bridge.internal.InboundBridgeUntrackedSpec"),
        "SendFailedRejectBridgeSpec" -> Set("blended.jms.bridge.internal.SendFailedRejectBridgeSpec")
      )
      object test extends Cross[Test](crossTestGroups: _*)
      class Test(override val testGroup: String) extends ForkedTest {
        override def otherModule: ForkedTest =  bridge.test(otherTestGroup)
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.activeMqBroker,
          deps.scalacheck,
          deps.scalatestplusScalacheck,
          deps.springCore,
          deps.springBeans,
          deps.springContext,
          deps.springExpression,
          deps.akkaSlf4j
        )}
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.activemq.brokerstarter,
          blended.testsupport,
          blended.testsupport.pojosr,
          blended.streams.testsupport
        )
      }
    }
    object utils extends BlendedModule {
      override val description = "A bundle to provide a ConnectionFactory wrapper that monitors a single connection and is able to monitor the connection via an active ping."
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.jms11Spec
      )}

      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.domino,
        blended.mgmt.base,
        blended.container.context.api,
        blended.updater.config,
        blended.util.logging,
        blended.akka
      )
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.akkaSlf4j,
          deps.akkaStream,
          deps.activeMqBroker,
          deps.activeMqKahadbStore,
          deps.akkaTestkit
        )}
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport
        )
      }
    }
  }

  object jmx extends BlendedModule with BlendedJvmModule {
    override val description = "Helper bundle to expose the platform's MBeanServer as OSGI Service."
    override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
      deps.domino,
      deps.prickle,
      deps.typesafeConfig
    )}
    override def moduleDeps = super.moduleDeps ++ Seq(
      blended.util,
      blended.util.logging,
      blended.akka
    )

    override def exportPackages : Seq[String] = super.exportPackages ++ Seq(
      s"${blendedModule}.json",
      s"${blendedModule}.statistics"
    )

    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Option(s"${blendedModule}.internal.BlendedJmxActivator")
    )}
    object test extends Tests {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.scalacheck,
        deps.scalatestplusScalacheck
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport,
        blended.testsupport.pojosr
      )
    }
    object js extends Js {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.js.prickle,
        deps.js.scalacheck
      )}
    }
  }

  object jolokia extends BlendedModule {
    override val description : String = "Provide an Actor based Jolokia Client to access JMX resources of a container via REST"
    override def ivyDeps = super.ivyDeps() ++ Agg(
      deps.sprayJson,
      deps.jsonLenses,
      deps.slf4j,
      deps.sttp
    )
    override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
      blended.akka
    )
    object test extends Tests {
      override def runIvyDeps: Target[Loose.Agg[Dep]] = T{ super.runIvyDeps() ++ Agg(
        deps.jolokiaJvmAgent
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport
      )
      override def forkArgs: Target[Seq[String]] = T{
        val jarFile = runClasspath().find(f => f.path.last.startsWith("jolokia-jvm-")).get.path
        println(s"Using Jolokia agent from: $jarFile")
        super.forkArgs() ++ Seq(
          s"-javaagent:${jarFile.toIO.getPath()}=port=0,host=localhost"
        )
      }
    }
  }

  object launcher extends BlendedModule {
    override def description = "Provide an OSGi Launcher"
    override def ivyDeps = Agg(
      deps.cmdOption,
      deps.orgOsgi,
      deps.typesafeConfig,
      deps.logbackCore,
      deps.logbackClassic,
      deps.commonsDaemon
    )

    override def essentialImportPackage: Seq[String] = super.essentialImportPackage ++ Seq(
      "org.apache.commons.daemon;resolution:=optional",
      "de.tototec.cmdoption.*;resolution:=optional"
    )

    // TODO: filter resources
    // TODO: package launcher distribution zip
    override def moduleDeps = Seq(
      blended.util,
      blended.util.logging,
      blended.updater.config,
      blended.security.crypto
    )
    override def extraPublish = T{ Seq(
      PublishInfo(file = dist.zip(), classifier = Some("dist"), ivyConfig = "compile")
    )}

    object dist extends DistModule with BlendedCoursierModule {
      override def deps = blended.launcher.deps
      override def distName: T[String] = T{ s"${blended.launcher.artifactId()}-${blended.launcher.publishVersion()}" }
      override def sources: Sources = T.sources(millSourcePath / "src" / "runner" / "binaryResources")
      override def filteredSources: Sources = T.sources(millSourcePath / "src" / "runner" / "resources")
      override def filterProperties: Target[Map[String, String]] = T{ Map(
        "blended.launcher.version" -> blended.launcher.publishVersion(),
        "blended.updater.config.version" -> blended.updater.config.publishVersion(),
        "blended.util.version" -> blended.util.publishVersion(),
        "blended.util.logging.version" -> blended.util.logging.publishVersion(),
        "blended.security.crypto.version" -> blended.security.crypto.publishVersion(),
        "cmdoption.version" -> deps.cmdOption.dep.version,
        "org.osgi.core.version" -> deps.orgOsgi.dep.version,
        "scala.binary.version" -> deps.scalaBinVersion(scalaVersion()),
        "scala.library.version" -> scalaVersion(),
        "typesafe.config.version" -> deps.typesafeConfig.dep.version,
        "slf4j.version" -> deps.slf4jVersion,
        "logback.version" -> deps.logbackClassic.dep.version,
        "splunkjava.version" -> deps.splunkjava.dep.version,
        "httpcore.version" -> deps.httpCore.dep.version,
        "httpcorenio.version" -> deps.httpCoreNio.dep.version,
        "httpcomponents.version" -> deps.httpComponents.dep.version,
        "httpasync.version" -> deps.httpAsync.dep.version,
        "commonslogging.version" -> deps.commonsLogging.dep.version,
        "jsonsimple.version" -> deps.jsonSimple.dep.version
      )}

      override def libIvyDeps = T{ Agg(
        deps.cmdOption,
        deps.orgOsgi,
        deps.scalaLibrary(scalaVersion()),
        deps.scalaReflect(scalaVersion()),
        deps.slf4j,
        deps.splunkjava,
        deps.httpCore,
        deps.httpCoreNio,
        deps.httpComponents,
        deps.httpAsync,
        deps.commonsLogging,
        deps.jsonSimple
      )}

      override def libModules : Seq[PublishModule] = Seq(
        blended.domino,
        blended.launcher,
        blended.util.logging,
        blended.security.crypto
      )
    }

    object test extends Tests {
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport
      )
      def osgiFrameworkIvyDeps: Map[String, Dep] = Map(
        "Felix 5.0.0" -> ivy"org.apache.felix:org.apache.felix.framework:5.0.0",
        "Felix 5.6.10" -> ivy"org.apache.felix:org.apache.felix.framework:5.6.10",
//        "Eclipse OSGi 3.8.0" -> ivy"org.eclipse:org.eclipse.osgi:3.8.0.v20120529-1548",
        "Eclipse OSGi 3.10.100.v20150529-1857" -> ivy"org.osgi:org.eclipse.osgi:3.10.100.v20150529-1857",
        "Eclipse OSGi 3.12.50" -> ivy"org.eclipse.platform:org.eclipse.osgi:3.12.50",
//        "Eclipse OSGi 3.9.1.v20130814-1242" -> ivy"org.eclipse.birt.runtime:org.eclipse.osgi:3.9.1.v20130814-1242",
        "Eclipse OSGi 3.10.0.v20140606-1445" -> ivy"org.eclipse.birt.runtime:org.eclipse.osgi:3.10.0.v20140606-1445"
      )
      def resolvedOsgiFrameworks = T{
        Target.traverse(osgiFrameworkIvyDeps.toSeq){ case (name, dep) =>
          T.task { name -> resolveDeps(T.task { Agg(dep) })().toSeq.head }
        }().toMap
      }
      override def generatedSources: Target[Seq[PathRef]] = T{ super.generatedSources() ++ {
        val file = T.dest / "blended" / "launcher" / "TestOsgiFrameworks.scala"
        val body =
          s"""
            |package blended.launcher
            |
            |import java.io.File
            |
            |/** Generated with mill: The frameworks to use in the tests. */
            |object TestOsgiFrameworks {
            |  val frameworks: Map[String, String] = ${
              resolvedOsgiFrameworks().map { case (name, file) => s""""${name}" -> "${file.path}"""" }
                .mkString("Map(\n    ", ",\n    ", "  )")
              }
            |}
            |""".stripMargin
        os.write(file, body, createFolders = true)
        Seq(PathRef(T.dest))
      }}
    }
  }

  object mgmt extends Module {
    object agent extends BlendedModule {
      override val description : String = "Bundle to regularly report monitoring information to a central container hosting the container registry"
      override def ivyDeps = super.ivyDeps() ++ Agg(
        deps.orgOsgi,
        deps.akkaOsgi,
        deps.akkaHttp,
        deps.akkaStream
      )
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.akka,
        blended.updater.config,
        blended.util.logging,
        blended.prickle.akka.http
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.AgentActivator")
      )}
    }
    object mock extends BlendedModule {
      override val description : String = "Mock server to simulate a larger network of blended containers for UI testing"
      override def ivyDeps = super.ivyDeps() ++ Agg(
        deps.cmdOption,
        deps.akkaActor,
        deps.logbackClassic
      )
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.mgmt.base,
        blended.mgmt.agent,
        blended.container.context.impl,
        blended.util.logging
      )
      object test extends Tests
    }
    object repo extends BlendedModule {
      override val description : String = "File Artifact Repository"
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.domino,
        blended.updater.config,
        blended.util.logging,
        blended.mgmt.base
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.ArtifactRepoActivator")
      )}
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.lambdaTest,
          deps.akkaTestkit,
          deps.akkaSlf4j
        )}
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport
        )
      }

      object rest extends BlendedModule {
        override val description : String = "File Artifact Repository REST Service"
        override def ivyDeps = super.ivyDeps() ++ Agg(
          deps.akkaHttp
        )
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.domino,
          blended.updater.config,
          blended.mgmt.base,
          blended.mgmt.repo,
          blended.security.akka.http,
          blended.util,
          blended.util.logging,
          blended.akka.http
        )
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Bundle-Activator` = Some(s"${blendedModule}.internal.ArtifactRepoRestActivator")
        )}
        object test extends Cross[Test](crossTestGroups: _*)
        class Test(override val testGroup: String) extends ForkedTest {
          override def otherModule: ForkedTest =  rest.test(otherTestGroup)
        }
      }
    }
    object rest extends BlendedModule {
      override val description = "REST interface to accept POST's from distributed containers. These will be delegated to the container registry"
      override def ivyDeps = super.ivyDeps() ++ Agg(
        deps.akkaActor,
        deps.domino,
        deps.akkaHttp,
        deps.akkaHttpCore,
        deps.akkaStream
      )
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.util.logging,
        blended.akka.http,
        blended.updater.remote,
        blended.security.akka.http,
        blended.akka,
        blended.prickle.akka.http,
        blended.mgmt.repo
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.MgmtRestActivator")
      )}
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.akkaStreamTestkit,
          deps.akkaHttpTestkit,
          deps.sttp,
          deps.lambdaTest
        )}
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport,
          blended.testsupport.pojosr,
          blended.persistence.h2
        )
      }
    }
    object base extends BlendedModule {
      override val description = "Shared classes for management and reporting facility"
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.domino,
        blended.container.context.api,
        blended.util,
        blended.util.logging
      )
      object test extends Tests {
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport,
          blended.testsupport.pojosr
        )
      }
    }
    object service extends Module {
      object jmx extends BlendedModule {
        override val description : String = "A JMX based Service Info Collector"
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.domino,
          blended.util.logging,
          blended.akka,
          blended.updater.config
        )
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Bundle-Activator` = Some(s"${blendedModule}.internal.ServiceJmxActivator")
        )}
        object test extends Tests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            deps.akkaTestkit
          )}
        }
      }
    }
  }
  object persistence extends BlendedModule {
    override val description : String = "Provide a technology agnostic persistence API with pluggable Data Objects defined in other bundles"
    override def ivyDeps = super.ivyDeps() ++ Agg(
      deps.slf4j,
      deps.domino
    )
    override def moduleDeps = super.moduleDeps ++ Seq(
      blended.akka
    )
    object test extends Tests {
      override def ivyDeps = super.ivyDeps() ++ Agg(
        deps.mockitoAll,
        deps.slf4jLog4j12
      )
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport
      )
    }

    object h2 extends BlendedModule {
      override val description : String = "Implement a persistence backend with H2 JDBC database"
      override def ivyDeps = super.ivyDeps() ++ Agg(
        deps.slf4j,
        deps.domino,
        deps.h2,
        deps.hikaricp,
        deps.springBeans,
        deps.springCore,
        deps.springTx,
        deps.springJdbc,
        deps.liquibase,
        deps.snakeyaml
      )
      override def moduleDeps = super.moduleDeps ++ Seq(
        persistence,
        util.logging,
        util,
        testsupport
      )
      override def exportPackages : Seq[String] = Seq.empty
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.H2Activator"),
        `Private-Package` = Seq(
          s"${blendedModule}.internal",
          "blended.persistence.jdbc"
        )
      )}
      object test extends Tests {
        override def ivyDeps = super.ivyDeps() ++ Agg(
          deps.lambdaTest,
          deps.scalacheck
        )
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.updater.config.test
        )
      }
    }
  }

  object prickle extends BlendedModule {
    override val description : String = "OSGi package for Prickle and mircojson"
    override def ivyDeps = super.ivyDeps() ++ Agg(
      deps.prickle.exclude("*" -> "*"),
      deps.microjson.exclude("*" -> "*")
    )

    override def essentialImportPackage = super.essentialImportPackage ++ Seq(
      "prickle",
      "microjson"
    )

    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Bundle-Classpath` = Seq(".") ++ embeddedJars().map(_.path.last)
    )}
    override def exportContents: T[Seq[String]] = T{ Seq(
      s"prickle;version=${deps.prickleVersion};-split-package:=merge-first",
      s"microjson;version=${deps.microJsonVersion};-split-package:=merge-first"
    )}
    override def embeddedJars: T[Seq[PathRef]] = T{
      compileClasspath().toSeq.filter(f =>
        f.path.last.startsWith("prickle") || f.path.last.startsWith("microjson")
      )
    }

    object akka extends Module {
      object http extends BlendedModule {
        override val description : String = "Define some convenience to use Prickle with Akka HTTP"
        override def ivyDeps = super.ivyDeps() ++ Agg(
          deps.akkaHttpCore,
          deps.akkaHttp,
          deps.akkaStream,
          deps.prickle
        )
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.util.logging
        )
        object test extends Tests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            deps.akkaStreamTestkit,
            deps.akkaHttpTestkit
          )}
        }
      }
    }
  }

  object security extends BlendedModule with BlendedJvmModule {
    override def description = "Configuration bundle for the security framework"
    override def ivyDeps = Agg(
      deps.prickle
    )
    override def moduleDeps = Seq(
      blended.util.logging,
      blended.domino,
      blended.util,
      blended.security.boot
    )
    override def exportPackages : Seq[String] = super.exportPackages ++ Seq(
      s"${blendedModule}.json"
    )
    override def osgiHeaders = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Some(s"${blendedModule}.internal.SecurityActivator")
    )}
    object test extends Tests {
      override def sources: Sources = T.sources { super.sources() ++ Seq(
        PathRef(baseDir / "blended.security.test" / "src" / "test" / "scala")
      )}
      override def testResources: Sources = T.sources { super.testResources() ++ Seq(
        PathRef(baseDir / "blended.security.test" / "src" / "test" / "resources")
      )}

      override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
        blended.testsupport,
        blended.testsupport.pojosr,
        blended.security.login.impl
      )
    }
    object js extends Js {
      override def ivyDeps = Agg(
        deps.js.prickle
      )
      object test extends Tests
    }

    object akka extends Module {
      object http extends BlendedModule {
        override val description : String = "Some security aware Akka HTTP routes for the blended container"
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.akkaHttp,
          deps.akkaStream,
          deps.orgOsgi,
          deps.orgOsgiCompendium,
          deps.slf4j
        )}
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.akka,
          blended.security,
          blended.util.logging
        )
        object test extends Tests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            deps.commonsBeanUtils,
            deps.akkaStreamTestkit,
            deps.akkaHttpTestkit
          )}
          override def moduleDeps = super.moduleDeps ++ Seq(
            blended.testsupport
          )
        }
      }
    }

    object login extends Module {
      object api extends BlendedModule {
        override val description : String = "API to provide the backend for a Login Service"
        override def ivyDeps = super.ivyDeps() ++ Agg(
          deps.prickle,
          deps.jjwt
        )
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.domino,
          blended.akka,
          blended.security
        )
        override def essentialImportPackage: Seq[String] = Seq("android.*;resolution:=optional")
        object test extends Tests {
          override def moduleDeps = super.moduleDeps ++ Seq(
            blended.testsupport,
            blended.testsupport.pojosr
          )
        }
      }
      object impl extends BlendedModule {
        override val description : String = "Implementation of the Login backend"
        override def ivyDeps = super.ivyDeps() ++ Agg(
          deps.jjwt,
          deps.bouncyCastleBcprov
        )
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.security.login.api
        )
        override def essentialImportPackage: Seq[String] = Seq("android.*;resolution:=optional")
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Bundle-Activator` = Some(s"${blendedModule}.LoginActivator"),
          `Bundle-Classpath` = Seq(".") ++ embeddedJars().map(_.path.last)
        )}
        override def embeddedJars: T[Seq[PathRef]] = T {
          super.embeddedJars() ++
          compileClasspath().toSeq.filter(f => f.path.last.startsWith("bcprov") || f.path.last.startsWith("jjwt"))
        }
        object test extends Tests {
          override def moduleDeps = super.moduleDeps ++ Seq(
            blended.testsupport,
            blended.testsupport.pojosr
          )
        }
      }
      object rest extends BlendedModule {
        override val description : String = "A REST service providing login services and web token management"
        override def ivyDeps = super.ivyDeps() ++ Agg(
          deps.akkaHttp,
          deps.akkaHttpCore
        )
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.akka.http,
          blended.security.akka.http,
          blended.util.logging,
          blended.security.login.api
        )
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Bundle-Activator` = Some(s"${blendedModule}.internal.RestLoginActivator")
        )}
        override def testGroups: Map[String, Set[String]] = Map(
          "LoginServiceSpec" -> Set("blended.security.login.rest.internal.LoginServiceSpec")
        )
        object test extends Cross[Test](crossTestGroups: _*)
        class Test(override val testGroup: String) extends ForkedTest {
          override def otherModule: ForkedTest =  rest.test(otherTestGroup)
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            deps.akkaTestkit,
            deps.akkaStreamTestkit,
            deps.akkaHttpTestkit,
            deps.sttp,
            deps.sttpAkka
          )}
          override def moduleDeps = super.moduleDeps ++ Seq(
            blended.testsupport,
            blended.testsupport.pojosr,
            blended.security.login.impl
          )
        }
      }
    }

    object boot extends BlendedModule {
      override def description: String = "A delegating login module for the blended container"
      override def compileIvyDeps: Target[Agg[Dep]] = Agg(
        deps.orgOsgi
      )
      override def osgiHeaders = T { super.osgiHeaders().copy(
        `Fragment-Host` = Some("system.bundle;extension:=framework"),
        `Import-Package` = Seq("")
      )}
    }

    object crypto extends BlendedModule {
      override def description = "Provides classes and mainline for encrypting / decrypting arbitrary Strings"
      override def ivyDeps = Agg(
        deps.cmdOption
      )
      override def essentialImportPackage: Seq[String] = Seq("de.tototec.cmdoption;resolution:=optional")
      object test extends Tests {
        override def ivyDeps = super.ivyDeps() ++ Agg(
          deps.scalacheck,
          deps.scalatestplusScalacheck,
          deps.logbackCore,
          deps.logbackClassic,
          deps.osLib
        )
        override def moduleDeps = super.moduleDeps ++ Seq(
          testsupport
        )
      }
    }

    object ssl extends BlendedModule {
      override val description = "Bundle to provide simple Server Certificate Management"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.domino,
        deps.bouncyCastleBcprov,
        deps.bouncyCastlePkix
      )}
      override def testGroups: Map[String, Set[String]] = Map(
        "CertificateActivatorSpec" -> Set("blended.security.ssl.internal.CertificateActivatorSpec")
      )
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        domino,
        util.logging,
        util,
        mgmt.base
      )

      override def embeddedJars : T[Seq[PathRef]] = T {
        compileClasspath().toSeq.filter(_.path.last.startsWith("bcp"))
      }

      override def osgiHeaders = T { super.osgiHeaders().copy(
        `Bundle-Activator` = Option(s"$blendedModule.internal.CertificateActivator"),
        `Bundle-Classpath` = Seq(".") ++ embeddedJars().map(_.path.last),
        `Private-Package` = Seq(
          s"$blendedModule.internal"
        )
      )}

      object test extends Cross[Test](crossTestGroups: _*)
      class Test(override val testGroup: String) extends ForkedTest {
        override def forkArgs: Target[Seq[String]] = T{ super.forkArgs() ++ Seq(
          s"-Djava.security.properties=${copiedResources().path.toIO.getPath()}/container/security.properties"
        )}
        override def otherModule: ForkedTest =  ssl.test(otherTestGroup)
        override def ivyDeps: Target[Loose.Agg[Dep]] = T { super.ivyDeps() ++ Agg(
          deps.scalacheck,
          deps.scalatestplusScalacheck
        )}
        override def runIvyDeps: Target[Loose.Agg[Dep]] = T { super.runIvyDeps() ++ Agg(
          deps.logbackClassic,
          deps.jclOverSlf4j,
          deps.springExpression
        )}
        override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
          testsupport,
          testsupport.pojosr
        )
      }
    }

    object scep extends BlendedModule {
      override val description : String = "Bundle to manage the container certificate via SCEP."
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.bouncyCastlePkix,
        deps.bouncyCastleBcprov,
        deps.commonsIo,
        deps.commonsLang2,
        deps.commonsCodec,
        deps.jcip,
        deps.jscep
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        domino,
        security.ssl,
        util.logging
      )
      private val embeddedPrefixes : Seq[String] = Seq(
        "bcprov", "bcpkix", "commons-io", "commons-lang", "commons-codec", "jcip-annotations",
        "jscep"
      )
      override def embeddedJars : T[Seq[PathRef]] = T {
        compileClasspath().toSeq.filter(d => embeddedPrefixes.exists(p => d.path.last.startsWith(p)))
      }
      override def osgiHeaders = T { super.osgiHeaders().copy(
        `Bundle-Activator` = Option(s"$blendedModule.internal.ScepActivator"),
        `Bundle-Classpath` = Seq(".") ++ embeddedJars().map(_.path.last),
        `Private-Package` = Seq(
          s"$blendedModule.internal"
        )
      )}
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.logbackClassic
        )}
        override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
          testsupport,
          testsupport.pojosr
        )
      }

      object standalone extends BlendedModule {
        override def description: String = "Standalone client to manage certificates via SCEP"
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.felixConnect,
          deps.domino,
          deps.typesafeConfig,
          deps.slf4j,
          deps.orgOsgi,
          deps.cmdOption,
          deps.jcip,
          deps.jscep,
          deps.bouncyCastlePkix,
          deps.bouncyCastleBcprov,
          deps.commonsIo,
          deps.commonsLang2,
          deps.commonsCodec,
          deps.logbackCore,
          deps.logbackClassic,
          deps.jclOverSlf4j
        )}
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          security.scep,
          security.ssl,
          container.context.impl,
          util.logging,
          container.context.api,
          domino,
          updater.config
        )
        object test extends Tests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            deps.osLib
          )}
          override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
            blended.testsupport
          )
        }
      }
    }
  }

  object streams extends BlendedModule {
    override val description : String = "Helper objects to work with Streams in blended integration flows."
    override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
      deps.akkaActor,
      deps.akkaStream,
      deps.akkaPersistence,
      deps.geronimoJms11Spec,
      deps.levelDbJava
    )}
    override def moduleDeps = super.moduleDeps ++ Seq(
      blended.util.logging,
      blended.jms.utils,
      blended.akka,
      blended.persistence,
      blended.jmx
    )
    override def exportPackages : Seq[String] = super.exportPackages ++ Seq(
      s"${blendedModule}.file",
      s"${blendedModule}.jms",
      s"${blendedModule}.message",
      s"${blendedModule}.processor",
      s"${blendedModule}.persistence",
      s"${blendedModule}.transaction",
      s"${blendedModule}.worklist"
    )

    override def osgiHeaders = T { super.osgiHeaders().copy(
      `Bundle-Activator` = Option(s"$blendedModule.internal.BlendedStreamsActivator")
    )}

    override def testGroups: Map[String, Set[String]] = Map(
      "JmsAckSourceSpec" -> Set("blended.streams.jms.JmsAckSourceSpec"),
      "FlowTransactionStreamSpec" -> Set("blended.streams.transaction.FlowTransactionStreamSpec"),
      "JmsRetryProcessorForwardSpec" -> Set("blended.streams.jms.JmsRetryProcessorForwardSpec"),
      "JmsRetryProcessorSendToRetrySpec" -> Set("blended.streams.jms.JmsRetryProcessorSendToRetrySpec"),
      "JmsRetryProcessorRetryCountSpec" -> Set("blended.streams.jms.JmsRetryProcessorRetryCountSpec"),
      "JmsRetryProcessorMissingDestinationSpec" -> Set("blended.streams.jms.JmsRetryProcessorMissingDestinationSpec"),
      "ParallelFileSourceSpec" -> Set("blended.streams.file.ParallelFileSourceSpec"),
      "FlowTransactionEventSpec" -> Set("blended.streams.transaction.FlowTransactionEventSpec"),
      "JmsRetryProcessorFailedSpec" -> Set("blended.streams.jms.JmsRetryProcessorFailedSpec"),
      "JmsKeepAliveActorSpec" -> Set("blended.streams.jms.internal.JmsKeepAliveActorSpec"),
      "FileSourceSpec" -> Set("blended.streams.file.FileSourceSpec"),
      "JmsRetryProcessorRetryTimeoutSpec" -> Set("blended.streams.jms.JmsRetryProcessorRetryTimeoutSpec"),
      "BulkCleanupSpec" -> Set("blended.streams.transaction.BulkCleanupSpec"),
      "FileFlowTransactionManagerSpec" -> Set("blended.streams.transaction.FileFlowTransactionManagerSpec")
    )
    object test extends Cross[Test](crossTestGroups: _*)
    class Test(override val testGroup: String) extends ForkedTest {
      override def otherModule: ForkedTest = streams.test(otherTestGroup)
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.commonsIo,
        deps.scalacheck,
        deps.scalatestplusScalacheck,
        deps.akkaTestkit,
        deps.akkaSlf4j,
        deps.activeMqBroker,
        deps.activeMqKahadbStore,
        deps.logbackClassic
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.activemq.brokerstarter,
        blended.persistence.h2,
        blended.testsupport.pojosr,
        blended.testsupport
      )
    }
    object dispatcher extends BlendedModule {
      override val description : String = "A generic Dispatcher to support common integration routing."
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{super.ivyDeps() ++ Agg(
        deps.akkaActor,
        deps.akkaStream,
        deps.geronimoJms11Spec,
        deps.akkaPersistence,
        deps.levelDbJava
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.util.logging,
        blended.streams,
        blended.jms.bridge,
        blended.akka,
        blended.persistence
      )
      override def exportPackages : Seq[String] = super.exportPackages ++ Seq(
        s"${blendedModule}.cbe"
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.DispatcherActivator")
      )}
      override def testGroups: Map[String, Set[String]] = Map(
        "TransactionOutboundSpec" -> Set("blended.streams.dispatcher.internal.builder.TransactionOutboundSpec"),
        "CbeFlowSpec" -> Set("blended.streams.dispatcher.internal.builder.CbeFlowSpec"),
        "DispatcherActivatorSpec" -> Set("blended.streams.dispatcher.internal.DispatcherActivatorSpec")
      )
      object test extends Cross[Test](crossTestGroups: _*)
      class Test(override val testGroup: String) extends ForkedTest {
        override def otherModule: ForkedTest = dispatcher.test(otherTestGroup)
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.akkaTestkit,
          deps.akkaSlf4j,
          deps.activeMqBroker,
          deps.activeMqKahadbStore,
          deps.logbackClassic,
          deps.asciiRender,
          deps.springCore,
          deps.springBeans,
          deps.springContext,
          deps.springExpression
        )}
        override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
          blended.persistence.h2,
          blended.activemq.brokerstarter,
          blended.streams.testsupport,
          blended.testsupport.pojosr,
          blended.testsupport
        )
      }
    }
    object testsupport extends BlendedModule {
      override val description : String = "Some classes to make testing for streams a bit easier"
      override def ivyDeps = Agg(
        deps.scalacheck,
        deps.scalatest,
        deps.akkaTestkit,
        deps.akkaActor,
        deps.akkaStream,
        deps.akkaPersistence,
        deps.logbackClassic
      )
      override def moduleDeps = Seq(
        blended.util.logging,
        blended.streams,
        blended.testsupport
      )

      object test extends Tests

    }
  }

  object testsupport extends BlendedModule {
    override def description = "Some test helper classes"
    override def ivyDeps = Agg(
      deps.akkaActor,
      deps.akkaTestkit,
      deps.akkaCamel,
      deps.jaxb,
      deps.scalatest,
      deps.junit,
      deps.commonsIo
    )
    override def moduleDeps = Seq(
      blended.util,
      blended.util.logging,
      blended.security.boot
    )
    object test extends Tests

    object pojosr extends BlendedModule {
      override def description = "A simple pojo based test container that can be used in unit testing"
      override def ivyDeps = Agg(
        deps.akkaTestkit,
        deps.scalatest,
        deps.felixConnect,
        deps.orgOsgi
      )
      override def moduleDeps = Seq(
        blended.testsupport,
        blended.util.logging,
        blended.container.context.impl,
        blended.domino,
        blended.jms.utils,
        blended.jmx,
        blended.akka.http,
        blended.streams,
        blended.streams.dispatcher
      )
      object test extends Tests
    }

  }

  object updater extends BlendedModule {
    override val description = "OSGi Updater"
    override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
      deps.orgOsgi,
      deps.domino,
      deps.akkaOsgi,
      deps.slf4j,
      deps.typesafeConfig
    )}
    override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
      blended.updater.config,
      blended.launcher,
      blended.mgmt.base,
      blended.container.context.api,
      blended.akka
    )
    override def essentialImportPackage: Seq[String] = Seq("blended.launcher.runtime;resolution:=optional")
    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Option(s"${blendedModule}.internal.BlendedUpdaterActivator")
    )}
    object test extends Tests {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.akkaTestkit,
        deps.felixFramework,
        deps.logbackClassic,
        deps.akkaSlf4j,
        deps.felixGogoRuntime,
        deps.felixGogoShell,
        deps.felixGogoCommand,
        deps.felixFileinstall,
        deps.mockitoAll
      )}
      override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
        blended.testsupport
      )
    }

    object config extends BlendedModule with BlendedJvmModule {
      override def description = "Configurations for Updater and Launcher"
      override def ivyDeps = Agg(
        deps.prickle,
        deps.typesafeConfig
      )
      override def exportPackages : Seq[String] = Seq(
        // we have files in binaryResources and in classes, so we need to merge
        s"${blendedModule};-split-package:=merge-first",
        s"${blendedModule}.json",
        s"${blendedModule}.util"
      )
      override def moduleDeps = Seq(
        blended.util.logging,
        blended.security
      )
      object test extends Tests {
        override def ivyDeps = super.ivyDeps() ++ Agg(
          deps.scalatest,
          deps.scalatestplusScalacheck,
          deps.logbackClassic,
          deps.logbackCore,
          deps.scalacheck
        )
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport,
          blended.util.logging
        )
      }

      object js extends Js {
        override def moduleDeps: Seq[PublishModule] = Seq(
          blended.security.js
        )
        object test extends Tests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            deps.js.prickle,
            deps.js.scalacheck
          )}
        }
      }
    }

    object remote extends BlendedModule {
      override val description = "OSGi Updater remote handle support"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.orgOsgi,
        deps.domino,
        deps.akkaOsgi,
        deps.slf4j,
        deps.typesafeConfig
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.util.logging,
        blended.persistence,
        blended.updater.config,
        blended.mgmt.base,
        blended.launcher,
        blended.container.context.api,
        blended.akka
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Option(s"${blendedModule}.internal.RemoteUpdaterActivator")
      )}
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.akkaTestkit,
          deps.felixFramework,
          deps.logbackClassic,
          deps.akkaSlf4j,
          deps.felixGogoRuntime,
          deps.felixGogoShell,
          deps.felixGogoCommand,
          deps.felixFileinstall,
          deps.mockitoAll,
          deps.springCore,
          deps.springBeans,
          deps.springContext,
          deps.springExpression,
          deps.jclOverSlf4j
        )}
        override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
          blended.testsupport,
          blended.persistence.h2
        )
      }
    }
    object tools extends BlendedModule {
      override val description = "Configurations for Updater and Launcher"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.typesafeConfig,
        deps.cmdOption
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.updater.config
      )
      object test extends Tests
    }

  }

  object util extends BlendedModule {
    override def description: String = "Utility classes to use in other bundles"
    override def compileIvyDeps: Target[Agg[Dep]] = Agg(
      deps.akkaActor,
      deps.akkaSlf4j,
      deps.slf4j,
      deps.typesafeConfig
    )
    override def exportPackages : Seq[String] = super.exportPackages ++ Seq(
      s"${blendedModule}.config",
      s"${blendedModule}.arm"
    )
    object test extends Tests {
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        deps.akkaTestkit,
        deps.junit,
        deps.logbackClassic,
        deps.logbackCore
      )}
    }
    object logging extends BlendedModule with BlendedJvmModule {
      override def description: String = "Logging utility classes to use in other bundles"
      override def compileIvyDeps: Target[Agg[Dep]] = Agg(
        deps.slf4j
      )
      object test extends Tests
    }
  }

  object websocket extends BlendedModule with BlendedJvmModule {
    override val description = "The web socket server module"
    override def ivyDeps = super.ivyDeps() ++ Agg(
      deps.akkaHttp,
      deps.akkaHttpCore
    )
    override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
      blended.akka,
      blended.akka.http,
      blended.security.login.api,
      blended.jmx
    )
    override def exportPackages : Seq[String] = super.exportPackages ++ Seq(
      s"${blendedModule}.json"
    )
    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Some(s"${blendedModule}.internal.WebSocketActivator")
    )}

    override def resources: Sources = T.sources {

      val dest = T.ctx().dest

      val versionResource : PathRef = {
        val props = s"""version="${blendedVersion()}""""
        val versionPath = dest / "generated"
        os.write(versionPath / "blended" / "websocket" / "internal" / "version.properties", props, createFolders = true)
        PathRef(versionPath)
      }
      super.resources() ++ Seq(versionResource)
    }
    object test extends Tests {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.akkaTestkit,
        deps.sttp,
        deps.sttpAkka,
        deps.logbackClassic,
        deps.springCore,
        deps.springBeans,
        deps.springContext,
        deps.springContext,
        deps.springExpression
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport,
        blended.persistence,
        blended.persistence.h2,
        blended.security.login.impl,
        blended.testsupport.pojosr,
        blended.security.login.rest
      )
    }

    object js extends Js {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.js.prickle
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.jmx.js
      )
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.js.scalacheck
        )}
      }
    }
  }

  object itestsupport extends BlendedModule {
    override def description = "Integration test helper classes"
    override def ivyDeps = T { super.ivyDeps() ++  Agg(
        deps.activeMqBroker,
        deps.akkaActor,
        deps.akkaStream,
        deps.akkaStreamTestkit,
        deps.akkaTestkit,
        deps.akkaHttpTestkit,
        deps.sttp,
        deps.dockerJava,
        deps.commonsCompress,
        deps.sttp,
        deps.sttpAkka
      )
    }
    override def moduleDeps = super.moduleDeps ++ Seq(
      blended.akka,
      blended.util,
      blended.jms.utils,
      blended.jolokia,
      blended.security.ssl
    )
    object test extends Tests {
      override def ivyDeps = T { super.ivyDeps() ++ Agg(
        deps.jolokiaJvmAgent,
        deps.scalatestplusMockito,
        deps.mockitoAll
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport
      )

      override def forkArgs = T {
        val jolokiaAgent : PathRef = compileClasspath().filter{ r =>
          r.path.last.startsWith("jolokia")
        }.toSeq.head

        super.forkArgs() ++ Seq(
          s"-javaagent:${jolokiaAgent.path.toIO.getAbsolutePath()}=port=0,host=localhost"
        )
      }
    }
  }

}

object scoverage extends ScoverageReport {
  override def scalaVersion = Deps.Deps_2_12.scalaVersion
  override def scoverageVersion = Deps.Deps_2_12.scoverageVersion
}