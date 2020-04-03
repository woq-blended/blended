import java.io.FileOutputStream
import java.util.zip.ZipEntry

import scala.collection.immutable
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader
import scala.util.Try
import scala.util.matching.Regex
import scala.util.matching.Regex.quoteReplacement

import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import coursier.Repository
import coursier.maven.MavenRepository
import mill.api.{Ctx, Loose, Result}
import mill.{PathRef, _}
import mill.define.{Command, Input, Sources, Target, Task}
import mill.scalajslib.ScalaJSModule
import mill.scalajslib.api.ModuleKind
import mill.scalalib._
import mill.scalalib.api.CompilationResult
import mill.scalalib.publish._
import os.{Path, RelPath}
import sbt.testing.{Fingerprint, Framework}

// This import the mill-osgi plugin
import $ivy.`de.tototec::de.tobiasroeser.mill.osgi:0.2.0`
import de.tobiasroeser.mill.osgi._

import $file.build_util
import build_util.{ZipUtil, FilterUtil}

import $file.build_deps
import build_deps.Deps




/** Project directory. */
val baseDir: os.Path = build.millSourcePath


trait BlendedCoursierModule extends CoursierModule {
  private def zincWorker: ZincWorkerModule = mill.scalalib.ZincWorkerModule
  override def repositories: Seq[Repository] = zincWorker.repositories ++ Seq(
    MavenRepository("https://repo.spring.io/libs-release")
  )
}

trait BlendedModule extends SbtModule with BlendedCoursierModule with PublishModule with OsgiBundleModule {
  /** The blended module name. */
  def blendedModule: String = millModuleSegments.parts.mkString(".")
  override def artifactName: T[String] = blendedModule
  /** The module description. */
  def description: String = "Blended module ${blendedModule}"
  def scalaVersion = Deps.scalaVersion
  def publishVersion = T { blended.version() }
  override def millSourcePath: os.Path = baseDir / blendedModule
  override def resources = T.sources { super.resources() ++ Seq(
    PathRef(millSourcePath / 'src / 'main / 'binaryResources)
  )}
  override def bundleSymbolicName = blendedModule
  def pomSettings: T[PomSettings] = T {
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
  override def scalacOptions = Seq("-deprecation", "-target:jvm-1.8")

  trait Tests extends super.Tests {
    override def ivyDeps = T{ super.ivyDeps() ++ Agg(
      Deps.scalatest
    )}
    override def testFrameworks = Seq("org.scalatest.tools.Framework")
    /** Empty, we use [[testResources]] instead to model sbt behavior. */
    override def runIvyDeps: Target[Loose.Agg[Dep]] = T{ super.runIvyDeps() ++ Agg(
      Deps.logbackClassic,
      Deps.jclOverSlf4j
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
           |    <file>${baseDir.toIO.getPath()}/target/test-${moduleSpec}.log</file>
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
    /** A command, because this needs to run always to be always fresh, as we intend to write into that dir when executing tests.
     * This is in migration from sbt-like setup.
     */
    def copiedResources(): Command[PathRef] = T.command {
      val dest = T.ctx().dest
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
    override def runClasspath: Target[Seq[PathRef]] = T{ super.runClasspath() ++ Seq(logResources(), copiedResources()()) }
    override def forkArgs: Target[Seq[String]] = T{ super.forkArgs() ++ Seq(
      s"-DprojectTestOutput=${copiedResources()().path.toIO.getPath()}"
    )}
  }

  val defaultTestGroup = "other"
  /** A Map of groups with their belonging test suites.
   * The groups name  [[defaultTestGroup]] is reserved for all tests that don't need to run in an extra JVM. */
  def testGroups: Map[String, Set[String]] = Map()
  /** Test group names, derived from [[testGroups]]. */
  def crossTestGroups: Seq[String] = (Set(defaultTestGroup) ++ testGroups.keySet).toSeq

  /** A test module that only executed the tests from the configured [[ForkedTest#testGroup]]. */
  trait ForkedTest extends Tests {

    def testGroup: String = defaultTestGroup

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
          else defaultTestGroup
        }
        val groupNames: Map[String, Set[String]] = groups.mapValues(_.map(_._1.getName()).toSet)
        cl.close()
        Result.Success(groupNames)
      }
    }

    /** redirect to "other"-compile */
    override def compile: T[CompilationResult] = if(testGroup == defaultTestGroup) super.compile else otherModule.compile
    /** Override this to define the target which compiles the test sources */
    def otherModule: ForkedTest

    def checkTestGroups(): Command[Unit] = T.command {
      if(testGroup == defaultTestGroup) T{
        // only check in default cross instance "other"

        val anyGroupsDetected = detectTestGroups().keySet != Set(defaultTestGroup)
        val anyGroupsDefined = testGroups.nonEmpty
        val relevantGroups: PartialFunction[(String, Set[String]), Set[String]] = {
          case (name, tests) if name != defaultTestGroup => tests
        }
        val definedGroupedTests = testGroups.collect(relevantGroups).toSet
        val detectedGroupedTests = detectTestGroups().collect(relevantGroups).toSet

        if((anyGroupsDetected || anyGroupsDefined) && definedGroupedTests != detectedGroupedTests) {
          T.log.error(s"Test groups invalid. Detected the following explicit groups: ${detectTestGroups() - defaultTestGroup}.")
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
      val tests = if(testGroup == defaultTestGroup && testGroups.get(defaultTestGroup).isEmpty) {
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

  /** Show all compiled classes: `mill show __.classes` */
  def classes: T[Seq[Path]] = T {
    Try(os.walk(compile().classes.path)).getOrElse(Seq())
  }
}

trait BlendedJvmModule extends BlendedModule { jvmBase =>
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

  trait Js extends ScalaJSModule { jsBase =>
    override def millSourcePath = jvmBase.millSourcePath / os.up / "js"
    override def scalaJSVersion = Deps.scalaJsVersion
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
        jsBase.millSourcePath / "src" / "test" / "scala",
        jsBase.millSourcePath / os.up / "shared" / "src" / "test" / "scala"
      )
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        Deps.js.scalatest
      )}
      override def testFrameworks = Seq("org.scalatest.tools.Framework")
      override def moduleKind: T[ModuleKind] = T{ ModuleKind.CommonJSModule }
    }
  }
}

trait DistModule extends CoursierModule {
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

  def scalaVersion: Target[String] = T{ Deps.scalaVersion }

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
  def zip = T{
    val dest = T.ctx().dest
    val zip = dest / s"${distName()}.zip"

    val dirs =
      sources().map(_.path) ++ Seq(
        expandedFilteredSources().path,
        resolvedLibs().path
      )

    ZipUtil.createZip(
      outputPath = zip,
      inputPaths = dirs
    )
    PathRef(zip)
  }
}

object blended extends Module {
  def version = T.input {
    os.read(baseDir / "version.txt").trim()
  }

  object activemq extends Module {
    object brokerstarter extends BlendedModule {
      override val description : String =
        """A simple wrapper around an Active MQ broker that makes sure that the broker is completely
          |started before exposing a connection factory OSGi service""".stripMargin
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        Deps.activeMqBroker,
        Deps.activeMqSpring,
        Deps.springBeans,
        Deps.springContext,
        Deps.springCore
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
        override def otherModule: ForkedTest =  brokerstarter.test(defaultTestGroup)
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          Deps.activeMqKahadbStore,
          Deps.springCore,
          Deps.springBeans,
          Deps.springContext,
          Deps.springExpression
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
        Deps.activeMqClient
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.util,
        blended.util.logging,
        blended.jms.utils,
        blended.akka,
        blended.streams
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.AmqClientActivator"),
        `Export-Package` = Seq(blendedModule)
      )}
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          Deps.activeMqBroker,
          Deps.activeMqKahadbStore
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
      Deps.orgOsgi,
      Deps.akkaActor,
      Deps.domino
    )}
    override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
      blended.util.logging,
      blended.container.context.api,
      blended.domino
    )
    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Some(s"${blendedModule}.internal.BlendedAkkaActivator"),
      `Export-Package` = Seq(
        blendedModule,
        s"${blendedModule}.protocol"
      )
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
        Deps.domino,
        Deps.akkaStream,
        Deps.akkaOsgi,
        Deps.akkaHttp
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.container.context.api,
        blended.domino,
        blended.util,
        blended.util.logging,
        blended.akka
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.BlendedAkkaHttpActivator")
      )}
      object test extends Tests {

        override def forkArgs = T{ super.forkArgs() ++ Seq("-Dsun.net.client.defaultReadTimeout=3000")}

        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          Deps.akkaTestkit,
          Deps.akkaSlf4j,
          Deps.mockitoAll,
          Deps.akkaHttpTestkit,
          Deps.akkaStreamTestkit
        )}
        override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
          blended.testsupport,
          blended.testsupport.pojosr
        )
      }

      object api extends BlendedModule {
        override val description : String = "Package the Akka Http API into a bundle."
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          Deps.akkaHttp,
          Deps.akkaHttpCore,
          Deps.akkaParsing
        )}
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Import-Package` = Seq(
            """scala.*;version="[2.12,2.12.50]"""",
            "com.sun.*;resolution:=optional",
            "sun.*;resolution:=optional",
            "net.liftweb.*;resolution:=optional",
            "play.*;resolution:=optional",
            "twirl.*;resolution:=optional",
            "org.json4s.*;resolution:=optional",
            "*"
          ),
          `Export-Package` = Seq(
            s"akka.http.*;version=${Deps.akkaHttpVersion};-split-package:=merge-first"
          )
        )}
//        override def exportContents: T[Seq[String]] = T{ Seq(
//          s"akka.http.*;version=${Deps.akkaHttpVersion};-split-package:=merge-first"
//        )}
//        override def embeddedJars: T[Seq[PathRef]] = T{
//          resolveDeps(T.task{ ivyDeps().map(_.exclude("*" -> "*")) })().toSeq
//        }
      }

      object jmsqueue extends BlendedModule {
        override val description : String = "Provide a simple REST interface to consume messages from JMS Queues"
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          Deps.domino,
          Deps.jms11Spec
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
            Deps.sttp,
            Deps.sttpAkka,
            Deps.akkaSlf4j,
            Deps.akkaTestkit,
            Deps.akkaStreamTestkit,
            Deps.akkaHttpTestkit,
            Deps.activeMqBroker,
            Deps.activeMqKahadbStore
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
          Deps.domino,
          Deps.akkaStream,
          Deps.akkaHttp,
          Deps.akkaActor
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
            Deps.akkaSlf4j,
            Deps.akkaTestkit,
            Deps.akkaStreamTestkit,
            Deps.akkaHttpTestkit
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
          Deps.domino,
          Deps.akkaStream,
          Deps.akkaHttp,
          Deps.akkaActor,
          Deps.jms11Spec
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
          override def otherModule: ForkedTest = restjms.test(defaultTestGroup)
          override def ivyDeps = T{ super.ivyDeps() ++ Agg(
            Deps.sttp,
            Deps.sttpAkka,
            Deps.activeMqBroker,
            Deps.activeMqClient,
            Deps.springBeans,
            Deps.springContext,
            Deps.akkaSlf4j,
            Deps.akkaTestkit
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
            Deps.domino,
            Deps.orgOsgi,
            Deps.orgOsgiCompendium,
            Deps.slf4j
          )}
          override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
            blended.akka,
            blended.akka.http,
            blended.akka.http.api
          )
          object test extends Tests {
            override def ivyDeps = T{ super.ivyDeps() ++ Agg(
              Deps.slf4jLog4j12,
              Deps.akkaStreamTestkit,
              Deps.akkaHttpTestkit
            )}
          }
        }
      }
    }

    object logging extends BlendedModule {
      override val description = "Redirect Akka Logging to the Blended logging framework"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        Deps.akkaActor
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
          Deps.typesafeConfig
        )
        override def osgiHeaders = T { super.osgiHeaders().copy(
          `Import-Package` = Seq(
            "blended.launcher.runtime;resolution:=optional",
            "*"
          )
        )}
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
          Deps.orgOsgiCompendium,
          Deps.orgOsgi,
          Deps.domino,
          Deps.slf4j,
          Deps.julToSlf4j,
          Deps.springExpression,
          Deps.springCore
        )
        override def moduleDeps = Seq(
          blended.security.crypto,
          blended.container.context.api,
          blended.util.logging,
          blended.util,
          blended.updater.config,
          blended.launcher
        )
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Bundle-Activator` = Some(s"${blendedModule}.internal.ContainerContextActivator"),
          `Import-Package` = Seq(
            "blended.launcher.runtime;resolution:=optional",
            "*"
          )
        )}
        object test extends Tests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            Deps.scalacheck
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
      Deps.typesafeConfig,
      Deps.domino
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
        Deps.commonsIo,
        Deps.activeMqBroker,
        Deps.activeMqKahadbStore,
        Deps.akkaTestkit,
        Deps.akkaSlf4j
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
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Fragment-Host` = Some("io.hawt.hawtio-web"),
        `Import-Package` = Seq(
          "blended.security.boot",
          "com.sun.jndi.ldap;resolution:=optional"
        )
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
       Deps.domino,
        Deps.jettyOsgiBoot
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.domino,
        blended.util.logging
      )
      private val jettyVersion = """version="[9,4,20)""""
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
          s"org.eclipse.jetty;$jettyVersion"
        ),
        `Bundle-Classpath` = Seq(".") ++ embeddedJars().map(_.path.last)
      )}
      override def embeddedJars: T[Seq[PathRef]] = T{ super.embeddedJars() ++
        resolveDeps(T.task {
          Agg(Deps.jettyOsgiBoot.exclude("*" -> "*"))
        })().toSeq
      }
    }
  }

  object jms extends Module {
    object bridge extends BlendedModule {
      override val description : String = "A generic JMS bridge to connect the local JMS broker to en external JMS"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        Deps.akkaActor,
        Deps.akkaStream,
        Deps.typesafeConfig
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
        override def otherModule: ForkedTest =  bridge.test(defaultTestGroup)
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          Deps.activeMqBroker,
          Deps.scalacheck,
          Deps.springCore,
          Deps.springBeans,
          Deps.springContext,
          Deps.springExpression,
          Deps.akkaSlf4j
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
        Deps.jms11Spec
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
          Deps.akkaSlf4j,
          Deps.akkaStream,
          Deps.activeMqBroker,
          Deps.activeMqKahadbStore,
          Deps.akkaTestkit
        )}
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport
        )
      }
    }
  }

  object jmx extends BlendedJvmModule {
    override val description = "Helper bundle to expose the platform's MBeanServer as OSGI Service."
    override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
      Deps.domino,
      Deps.prickle,
      Deps.typesafeConfig
    )}
    override def moduleDeps = super.moduleDeps ++ Seq(
      blended.util,
      blended.util.logging,
      blended.akka
    )

    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Option(s"${blendedModule}.internal.BlendedJmxActivator"),
      `Export-Package` = Seq(
        blendedModule,
        s"${blendedModule}.json",
        s"${blendedModule}.statistics"
      )
    )}
    object test extends Tests {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        Deps.scalacheck
      )}
      override def runIvyDeps: Target[Loose.Agg[Dep]] = T{ super.runIvyDeps() ++ Agg(
//        Deps.springExpression
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport,
        blended.testsupport.pojosr
      )
    }
    object js extends Js {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        Deps.js.prickle,
        Deps.js.scalacheck
      )}
    }
  }

  object jolokia extends BlendedModule {
    override val description : String = "Provide an Actor based Jolokia Client to access JMX resources of a container via REST"
    override def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.sprayJson,
      Deps.jsonLenses,
      Deps.slf4j,
      Deps.sttp
    )
    override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
      blended.akka
    )
    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Export-Package` = Seq(blendedModule)
    )}
    object test extends Tests {
      override def runIvyDeps: Target[Loose.Agg[Dep]] = T{ super.runIvyDeps() ++ Agg(
        Deps.jolokiaJvmAgent
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport
      )
      override def forkArgs: Target[Seq[String]] = T{
        val jarFile = runClasspath().find(f => f.path.last.startsWith("jolokia-jvm-")).get.path
        println(s"Using Jolokia agent from: $jarFile")
        super.forkArgs() ++ Seq(
          s"-javaagent:${jarFile.toIO.getPath()}=port=7777,host=localhost"
        )
      }
    }
  }

  object launcher extends BlendedModule {
    override def description = "Provide an OSGi Launcher"
    override def ivyDeps = Agg(
      Deps.cmdOption,
      Deps.orgOsgi,
      Deps.typesafeConfig,
      Deps.logbackCore,
      Deps.logbackClassic,
      Deps.commonsDaemon
    )
    override def osgiHeaders = super.osgiHeaders().copy(
      `Import-Package` = Seq(
        "org.apache.commons.daemon;resolution:=optional",
        "de.tototec.cmdoption.*;resolution:=optional"
      )
    )
    // TODO: filter resources
    // TODO: package laucnher distribution zip
    override def moduleDeps = Seq(
      blended.util.logging,
      blended.updater.config,
      blended.security.crypto
    )
    override def extraPublish = T{ Seq(
      PublishModule.ExtraPublish(dist.zip(), "zips", ".zip")
    )}

    object dist extends DistModule with BlendedCoursierModule {
      override def distName: T[String] = T{ s"${blended.launcher.artifactId()}-${blended.launcher.publishVersion()}" }
      override def sources: Sources = T.sources(millSourcePath / "src" / "runner" / "binaryResources")
      override def filteredSources: Sources = T.sources(millSourcePath / "src" / "runner" / "resources")
      override def filterProperties: Target[Map[String, String]] = T{ Map(
        "blended.launcher.version" -> blended.launcher.publishVersion(),
        "blended.updater.config.version" -> blended.updater.config.publishVersion(),
        "blended.util.logging.version" -> blended.util.logging.publishVersion(),
        "blended.security.crypto.version" -> blended.security.crypto.publishVersion(),
        "cmdoption.version" -> Deps.cmdOption.dep.version,
        "org.osgi.core.version" -> Deps.orgOsgi.dep.version,
        "scala.binary.version" -> Deps.scalaBinVersion(scalaVersion()),
        "scala.library.version" -> scalaVersion(),
        "typesafe.config.version" -> Deps.typesafeConfig.dep.version,
        "slf4j.version" -> Deps.slf4jVersion,
        "logback.version" -> Deps.logbackClassic.dep.version,
        "splunkjava.version" -> Deps.splunkjava.dep.version,
        "httpcore.version" -> Deps.httpCore.dep.version,
        "httpcorenio.version" -> Deps.httpCoreNio.dep.version,
        "httpcomponents.version" -> Deps.httpComponents.dep.version,
        "httpasync.version" -> Deps.httpAsync.dep.version,
        "commonslogging.version" -> Deps.commonsLogging.dep.version,
        "jsonsimple.version" -> Deps.jsonSimple.dep.version
      )}

      override def scalaVersion: Target[String] = T{ blended.launcher.scalaVersion() }
      override def libIvyDeps = T{ Agg(
        Deps.cmdOption,
        Deps.orgOsgi,
        Deps.scalaLibrary(scalaVersion()),
        Deps.slf4j,
        Deps.splunkjava,
        Deps.httpCore,
        Deps.httpCoreNio,
        Deps.httpComponents,
        Deps.httpAsync,
        Deps.commonsLogging,
        Deps.jsonSimple
      )}
      override def libModules = Seq(
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
        Deps.orgOsgi,
        Deps.akkaOsgi,
        Deps.akkaHttp,
        Deps.akkaStream
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
        Deps.cmdOption,
        Deps.akkaActor,
        Deps.logbackClassic
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
          Deps.lambdaTest,
          Deps.akkaTestkit,
          Deps.akkaSlf4j
        )}
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport
        )
      }

      object rest extends BlendedModule {
        override val description : String = "File Artifact Repository REST Service"
        override def ivyDeps = super.ivyDeps() ++ Agg(
          Deps.akkaHttp
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
        override def testGroups: Map[String, Set[String]] = Map(
          "CollectorServicePojosrSpec" -> Set("blended.mgmt.rest.internal.CollectorServicePojosrSpec")
        )
        object test extends Cross[Test](crossTestGroups: _*)
        class Test(override val testGroup: String) extends ForkedTest {
          override def otherModule: ForkedTest =  rest.test(defaultTestGroup)
        }
      }
    }
    object rest extends BlendedModule {
      override val description = "REST interface to accept POST's from distributed containers. These will be delegated to the container registry"
      override def ivyDeps = super.ivyDeps() ++ Agg(
        Deps.akkaActor,
        Deps.domino,
        Deps.akkaHttp,
        Deps.akkaHttpCore,
        Deps.akkaStream
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
          Deps.akkaStreamTestkit,
          Deps.akkaHttpTestkit,
          Deps.sttp,
          Deps.lambdaTest
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
            Deps.akkaTestkit
          )}
        }
      }
    }
  }
  object persistence extends BlendedModule {
    override val description : String = "Provide a technology agnostic persistence API with pluggable Data Objects defined in other bundles"
    override def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.slf4j,
      Deps.domino
    )
    override def moduleDeps = super.moduleDeps ++ Seq(
      blended.akka
    )
    object test extends Tests {
      override def ivyDeps = super.ivyDeps() ++ Agg(
        Deps.mockitoAll,
        Deps.slf4jLog4j12
      )
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport
      )
    }

    object h2 extends BlendedModule {
      override val description : String = "Implement a persistence backend with H2 JDBC database"
      override def ivyDeps = super.ivyDeps() ++ Agg(
        Deps.slf4j,
        Deps.domino,
        Deps.h2,
        Deps.hikaricp,
        Deps.springBeans,
        Deps.springCore,
        Deps.springTx,
        Deps.springJdbc,
        Deps.liquibase,
        Deps.snakeyaml
      )
      override def moduleDeps = super.moduleDeps ++ Seq(
        persistence,
        util.logging,
        util,
        testsupport
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.H2Activator"),
        `Export-Package` = Seq(),
        `Private-Package` = Seq(
          s"${blendedModule}.internal",
          "blended.persistence.jdbc"
        )
      )}
      object test extends Tests {
        override def ivyDeps = super.ivyDeps() ++ Agg(
          Deps.lambdaTest,
          Deps.scalacheck
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
      Deps.prickle.exclude("*" -> "*"),
      Deps.microjson.exclude("*" -> "*")
    )
    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Import-Package` = Seq(
        "prickle",
        "microjson"
      ),
      `Bundle-Classpath` = Seq(".") ++ embeddedJars().map(_.path.last)
    )}
    override def exportContents: T[Seq[String]] = T{ Seq(
      s"prickle;version=${Deps.prickleVersion};-split-package:=merge-first",
      s"microjson;version=${Deps.microJsonVersion};-split-package:=merge-first"
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
          Deps.akkaHttpCore,
          Deps.akkaHttp,
          Deps.akkaStream,
          Deps.prickle
        )
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.util.logging
        )
        object test extends Tests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            Deps.akkaStreamTestkit,
            Deps.akkaHttpTestkit
          )}
        }
      }
    }
  }

  object security extends BlendedJvmModule {
    override def description = "Configuration bundle for the security framework"
    override def ivyDeps = Agg(
      Deps.prickle
    )
    override def moduleDeps = Seq(
      blended.util.logging,
      blended.domino,
      blended.util,
      blended.security.boot
    )
    override def osgiHeaders = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Some(s"${blendedModule}.internal.SecurityActivator"),
      `Export-Package` = Seq(
        blendedModule,
        s"${blendedModule}.json"
      )
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
        ivy"com.github.benhutchison::prickle::1.1.14"
      )
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          Deps.js.prickle
        )}
      }
    }

    object akka extends Module {
      object http extends BlendedModule {
        override val description : String = "Some security aware Akka HTTP routes for the blended container"
        override def ivyDeps = super.ivyDeps() ++ Agg(
          Deps.akkaHttp,
          Deps.akkaStream,
          Deps.orgOsgi,
          Deps.orgOsgiCompendium,
          Deps.slf4j
        )
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.akka,
          blended.security,
          blended.util.logging
        )
        object test extends Tests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            Deps.commonsBeanUtils,
            Deps.akkaStreamTestkit,
            Deps.akkaHttpTestkit
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
          Deps.prickle,
          Deps.jjwt
        )
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.domino,
          blended.akka,
          blended.security
        )
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Import-Package` = Seq(
            "android.*;resolution:=optional"
          )
        )}
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
          Deps.jjwt,
          Deps.bouncyCastleBcprov
        )
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.security.login.api
        )
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Bundle-Activator` = Some(s"${blendedModule}.LoginActivator"),
          `Import-Package` = Seq(
            "android.*;resolution:=optional"
          ),
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
          Deps.akkaHttp,
          Deps.akkaHttpCore
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
          override def otherModule: ForkedTest =  rest.test(defaultTestGroup)
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            Deps.akkaTestkit,
            Deps.akkaStreamTestkit,
            Deps.akkaHttpTestkit,
            Deps.sttp,
            Deps.sttpAkka
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
        Deps.orgOsgi
      )
      override def osgiHeaders = T { super.osgiHeaders().copy(
        `Fragment-Host` = Some("system.bundle;extension:=framework"),
        `Import-Package` = Seq("")
      )}
    }

    object crypto extends BlendedModule {
      override def description = "Provides classes and mainline for encrypting / decrypting arbitrary Strings"
      override def ivyDeps = Agg(
        Deps.cmdOption
      )
      override def osgiHeaders = T { super.osgiHeaders().copy(
        `Import-Package` = Seq(
          "de.tototec.cmdoption;resolution:=optional"
        ),
        `Export-Package` = Seq(
          blendedModule
        )
      )}
      object test extends Tests {
        override def ivyDeps = super.ivyDeps() ++ Agg(
          Deps.scalacheck,
          Deps.logbackCore,
          Deps.logbackClassic,
          Deps.osLib
        )
        override def moduleDeps = super.moduleDeps ++ Seq(
          testsupport
        )
      }
    }

    object ssl extends BlendedModule {
      override val description = "Bundle to provide simple Server Certificate Management"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        Deps.domino,
        Deps.bouncyCastleBcprov,
        Deps.bouncyCastlePkix
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
      object test extends Cross[Test](crossTestGroups: _*)
      class Test(override val testGroup: String) extends ForkedTest {
        override def forkArgs: Target[Seq[String]] = T{ super.forkArgs() ++ Seq(
          s"-Djava.security.properties=${copiedResources()().path.toIO.getPath()}/container/security.properties"
        )}
        override def otherModule: ForkedTest =  ssl.test(defaultTestGroup)
        override def ivyDeps: Target[Loose.Agg[Dep]] = T { super.ivyDeps() ++ Agg(
          Deps.scalacheck
        )}
        override def runIvyDeps: Target[Loose.Agg[Dep]] = T { super.runIvyDeps() ++ Agg(
          Deps.logbackClassic,
          Deps.jclOverSlf4j,
          Deps.springExpression
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
        Deps.bouncyCastlePkix,
        Deps.bouncyCastleBcprov,
        Deps.commonsIo,
        Deps.commonsLang2,
        Deps.commonsCodec,
        Deps.jcip,
        Deps.jscep
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        domino,
        security.ssl,
        util.logging
      )
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          Deps.logbackClassic
        )}
        override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
          testsupport,
          testsupport.pojosr
        )
      }

      object standalone extends BlendedModule {
        override def description: String = "Standalone client to manage certificates via SCEP"
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          Deps.felixConnect,
          Deps.domino,
          Deps.typesafeConfig,
          Deps.slf4j,
          Deps.orgOsgi,
          Deps.cmdOption,
          Deps.jcip,
          Deps.jscep,
          Deps.bouncyCastlePkix,
          Deps.bouncyCastleBcprov,
          Deps.commonsIo,
          Deps.commonsLang2,
          Deps.commonsCodec,
          Deps.logbackCore,
          Deps.logbackClassic,
          Deps.jclOverSlf4j
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
            Deps.osLib
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
      Deps.akkaActor,
      Deps.akkaStream,
      Deps.akkaPersistence,
      Deps.geronimoJms11Spec,
      Deps.levelDbJava
    )}
    override def moduleDeps = super.moduleDeps ++ Seq(
      blended.util.logging,
      blended.jms.utils,
      blended.akka,
      blended.persistence,
      blended.jmx
    )
    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Export-Package` = Seq(
        blendedModule,
        s"${blendedModule}.file",
        s"${blendedModule}.jms",
        s"${blendedModule}.message",
        s"${blendedModule}.processor",
        s"${blendedModule}.persistence",
        s"${blendedModule}.transaction",
        s"${blendedModule}.worklist"
      )
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
      override def otherModule: ForkedTest = streams.test(defaultTestGroup)
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        Deps.commonsIo,
        Deps.scalacheck,
        Deps.akkaTestkit,
        Deps.akkaSlf4j,
        Deps.activeMqBroker,
        Deps.activeMqKahadbStore,
        Deps.logbackClassic
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
        Deps.akkaActor,
        Deps.akkaStream,
        Deps.geronimoJms11Spec,
        Deps.akkaPersistence,
        Deps.levelDbJava
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.util.logging,
        blended.streams,
        blended.jms.bridge,
        blended.akka,
        blended.persistence
      )

      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.DispatcherActivator"),
        `Export-Package` = Seq(
          blendedModule,
          s"${blendedModule}.cbe"
        )
      )}
      override def testGroups: Map[String, Set[String]] = Map(
        "TransactionOutboundSpec" -> Set("blended.streams.dispatcher.internal.builder.TransactionOutboundSpec"),
        "CbeFlowSpec" -> Set("blended.streams.dispatcher.internal.builder.CbeFlowSpec"),
        "DispatcherActivatorSpec" -> Set("blended.streams.dispatcher.internal.DispatcherActivatorSpec")
      )
      object test extends Cross[Test](crossTestGroups: _*)
      class Test(override val testGroup: String) extends ForkedTest {
        override def otherModule: ForkedTest = dispatcher.test(defaultTestGroup)
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          Deps.akkaTestkit,
          Deps.akkaSlf4j,
          Deps.activeMqBroker,
          Deps.activeMqKahadbStore,
          Deps.logbackClassic,
          Deps.asciiRender,
          Deps.springCore,
          Deps.springBeans,
          Deps.springContext,
          Deps.springExpression
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
        Deps.scalacheck,
        Deps.scalatest,
        Deps.akkaTestkit,
        Deps.akkaActor,
        Deps.akkaStream,
        Deps.akkaPersistence,
        Deps.logbackClassic
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
      Deps.akkaActor,
      Deps.akkaTestkit,
      Deps.akkaCamel,
      Deps.jaxb,
      Deps.scalatest,
      Deps.junit,
      Deps.commonsIo
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
        Deps.akkaTestkit,
        Deps.scalatest,
        Deps.felixConnect,
        Deps.orgOsgi
      )
      override def moduleDeps = Seq(
        blended.util.logging,
        blended.container.context.impl,
        blended.domino,
        blended.jms.utils
      )
      object test extends Tests
    }

  }

  object updater extends BlendedModule {
    override val description = "OSGi Updater"
    override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
      Deps.orgOsgi,
      Deps.domino,
      Deps.akkaOsgi,
      Deps.slf4j,
      Deps.typesafeConfig
    )}
    override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
      blended.updater.config,
      blended.launcher,
      blended.mgmt.base,
      blended.container.context.api,
      blended.akka
    )
    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Option(s"${blendedModule}.internal.BlendedUpdaterActivator"),
      `Import-Package` = Seq(
        "blended.launcher.runtime;resolution:=optional"
      )
    )}
    object test extends Tests {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        Deps.akkaTestkit,
        Deps.felixFramework,
        Deps.logbackClassic,
        Deps.akkaSlf4j,
        Deps.felixGogoRuntime,
        Deps.felixGogoShell,
        Deps.felixGogoCommand,
        Deps.felixFileinstall,
        Deps.mockitoAll
      )}
      override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
        blended.testsupport
      )
    }

    object config extends BlendedJvmModule {
      override def description = "Configurations for Updater and Launcher"
      override def ivyDeps = Agg(
        Deps.prickle,
        Deps.typesafeConfig
      )
      override def osgiHeaders = super.osgiHeaders().copy(
        `Export-Package` = Seq(
          // we have files in binaryResources and in classes, so we need to merge
          s"${blendedModule};-split-package:=merge-first",
          s"${blendedModule}.json",
          s"${blendedModule}.util",
          // we have files in binaryResources and in classes, so we need to merge
          "blended.launcher.config;-split-package:=merge-first"
        )
      )
      override def moduleDeps = Seq(
        blended.util.logging,
        blended.security
      )
      object test extends Tests {
        override def ivyDeps = super.ivyDeps() ++ Agg(
          Deps.scalatest,
          Deps.logbackClassic,
          Deps.logbackCore,
          Deps.scalacheck,
          Deps.log4s
        )
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport
        )
      }

      object js extends Js {
        override def moduleDeps: Seq[JavaModule] = Seq(
          blended.security.js
        )
        object test extends Tests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            Deps.js.prickle,
            Deps.js.scalacheck,
            Deps.js.log4s
          )}
        }
      }
    }

    object remote extends BlendedModule {
      override val description = "OSGi Updater remote handle support"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        Deps.orgOsgi,
        Deps.domino,
        Deps.akkaOsgi,
        Deps.slf4j,
        Deps.typesafeConfig
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
          Deps.akkaTestkit,
          Deps.felixFramework,
          Deps.logbackClassic,
          Deps.akkaSlf4j,
          Deps.felixGogoRuntime,
          Deps.felixGogoShell,
          Deps.felixGogoCommand,
          Deps.felixFileinstall,
          Deps.mockitoAll,
          Deps.springCore,
          Deps.springBeans,
          Deps.springContext,
          Deps.springExpression,
          Deps.jclOverSlf4j
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
        Deps.typesafeConfig,
        Deps.cmdOption
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
      Deps.akkaActor,
      Deps.akkaSlf4j,
      Deps.slf4j,
      Deps.typesafeConfig
    )
    override def osgiHeaders = T { super.osgiHeaders().copy(
      `Export-Package` = Seq(
        blendedModule,
        s"${blendedModule}.config"
      )
    )}
    object test extends Tests {
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        Deps.akkaTestkit,
        Deps.junit,
        Deps.logbackClassic,
        Deps.logbackCore
      )}
    }
    object logging extends BlendedModule {
      override def description: String = "Logging utility classes to use in other bundles"
      override def compileIvyDeps: Target[Agg[Dep]] = Agg(
        Deps.slf4j
      )
      object test extends Tests
    }
  }

  object websocket extends BlendedJvmModule {
    override val description = "The web socket server module"
    override def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.akkaHttp,
      Deps.akkaHttpCore
    )
    override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
      blended.akka,
      blended.akka.http,
      blended.security.login.api,
      blended.jmx
    )

    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Some(s"${blendedModule}.internal.WebSocketActivator"),
      `Export-Package` = Seq(
        blendedModule,
        s"${blendedModule}.json"
      )
    )}

    override def resources: Sources = T.sources {

      val dest = T.ctx().dest

      val versionResource : PathRef = {
        val props = s"""version="${blended.version()}""""
        val versionPath = dest / "generated"
        os.write(versionPath / "blended" / "websocket" / "internal" / "version.properties", props, createFolders = true)
        PathRef(versionPath)
      }
      super.resources() ++ Seq(versionResource)
    }
    object test extends Tests {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        Deps.akkaTestkit,
        Deps.sttp,
        Deps.sttpAkka,
        Deps.logbackClassic,
        Deps.springCore,
        Deps.springBeans,
        Deps.springContext,
        Deps.springContext,
        Deps.springExpression
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
        Deps.js.prickle
      )}
      override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
        blended.jmx.js
      )
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          Deps.js.scalacheck,
          Deps.js.log4s
        )}
      }
    }
  }

}
