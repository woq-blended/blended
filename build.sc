import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`

import coursier.Repository
import mill.api.Loose
import mill.define.{Sources, Target, Task}
import mill.modules.{Jvm, Util}
import mill.scalalib._
import mill.scalalib.publish._
import mill._
import os.{Path, RelPath}
import coursier.maven.MavenRepository
import coursier.core.Authentication

// This import the mill-osgi plugin
import $ivy.`de.tototec::de.tobiasroeser.mill.osgi:0.3.0`
import de.tobiasroeser.mill.osgi._

// imports from the blended-mill plugin
import $ivy.`de.wayofquality.blended::blended-mill:0.4.3`
import de.wayofquality.blended.mill.versioning.GitModule
import de.wayofquality.blended.mill.publish.BlendedPublishModule
import de.wayofquality.blended.mill.webtools.WebTools
import de.wayofquality.blended.mill.modules._
import de.wayofquality.blended.mill.utils._
import de.wayofquality.blended.mill.feature._

import $file.build_util
import build_util.ScoverageReport

trait CoreDependencies extends BlendedDependencies

object CoreDependencies {
  def scalaVersions : Map[String, CoreDependencies] =
    Seq(Deps_2_13).map(d => d.scalaVersion -> d).toMap
  
  object Deps_2_13 extends CoreDependencies {
    override def scalaVersion = "2.13.2"
  }
  
  /////////////////////////////////////////////////////////////////////////////////////
  
  /** Project directory. */
}
val projectDir: os.Path = build.millSourcePath

/** The revision of bundles provided via akka-osgi */
val akkaBundleRevision : String = "1"

object GitSupport extends GitModule {
  override def millSourcePath: Path = projectDir
}

def blendedVersion = T { GitSupport.publishVersion() }

/** Configure additional repositories. */
trait CoreCoursierModule extends CoursierModule {
  private def zincWorker: ZincWorkerModule = mill.scalalib.ZincWorkerModule
  override def repositories: Seq[Repository] = {
    zincWorker.repositories ++ Seq(
      MavenRepository("https://repo.spring.io/libs-release"),
      MavenRepository("http://repository.springsource.com/maven/bundles/release"),
      MavenRepository("http://repository.springsource.com/maven/bundles/external"),
      MavenRepository(
        s"https://u233308-sub2.your-storagebox.de/akka-osgi/${akkaBundleRevision}",
        Some(Authentication("u233308-sub2", "px8Kumv98zIzSF7k"))
      )
    )
  }
}

/** Configure plublish settings. */
trait CorePublishModule extends BlendedPublishModule {
  override def description : T[String] = s"Blended module ${artifactName()}"

  def githubRepo : String = "blended"
  def scpTargetDir : String = "blended"

  override def publishVersion = T { blendedVersion() }
}

trait DistModule extends CoreCoursierModule {
  def deps: CoreDependencies
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

    jars.iterator.foreach { jar =>
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

trait JBakeBuild extends Module with WebTools {

  override def npmModulesDir : Path = projectDir / "node_modules"

  def jbakeVersion : String = "2.6.5"
  def jbakeDownloadUrl : String = s"https://dl.bintray.com/jbake/binary/jbake-${jbakeVersion}-bin.zip"

  def prepareJBake : T[PathRef] = T {
    Util.downloadUnpackZip(jbakeDownloadUrl, RelPath("."))

    PathRef(T.dest)
  }

  def jbake : T[PathRef] = T {

    val jbakeCp : Agg[Path] = os.list(prepareJBake().path / s"jbake-$jbakeVersion-bin" / "lib")

    val process = Jvm.runSubprocess(
      mainClass = "org.jbake.launcher.Main",
      classPath = jbakeCp,
      mainArgs = Seq(
        millSourcePath.toIO.getAbsolutePath(),
        T.dest.toIO.getAbsolutePath(),
        "-b"
      )
    )

    val webpackResult : Path = webpack().path

    os.walk(webpackResult).foreach { p =>
      val rel = p.relativeTo(webpackResult)
      val dest = T.dest / "webpack" / rel
      os.copy(p, dest, replaceExisting = true, createFolders = true)
    }
    PathRef(T.dest)
  }

}

object blended extends Cross[BlendedCross](CoreDependencies.scalaVersions.keys.toSeq: _*)
class BlendedCross(crossScalaVersion: String) extends GenIdeaModule { blended =>
  override def skipIdea: Boolean = crossScalaVersion != CoreDependencies.Deps_2_13.scalaVersion

  // correct the unneeded cross sub-dir
  override def millSourcePath: Path = super.millSourcePath / os.up
  val deps = CoreDependencies.scalaVersions(crossScalaVersion)

  trait CoreModule extends BlendedBaseModule
    with BlendedOsgiModule
    with CoreCoursierModule
    with CorePublishModule {

    override def scalaVersion : T[String] = deps.scalaVersion
    override def baseDir : os.Path = projectDir

    override type ProjectDeps = CoreDependencies
    override def deps = blended.deps

    // remove the scala version
    override def skipIdea: Boolean = crossScalaVersion != CoreDependencies.Deps_2_13.scalaVersion
    trait CoreTests extends super.BlendedTests {
      override def skipIdea: Boolean = crossScalaVersion != CoreDependencies.Deps_2_13.scalaVersion
    }
    trait CoreForkedTests extends super.BlendedForkedTests {
      override def skipIdea: Boolean = crossScalaVersion != CoreDependencies.Deps_2_13.scalaVersion
    }
  }

  trait CoreJvmModule extends CoreModule
    with BlendedJvmModule
    with BlendedOsgiModule
    with CorePublishModule {

    trait CoreJs extends super.BlendedJs
      with CorePublishModule
  }

  object doc extends JBakeBuild with WebTools {

    override def millSourcePath = projectDir / "doc"
  }

  object activemq extends Module {
    object brokerstarter extends CoreModule {
      override def description =
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
      class Test(override val testGroup: String) extends CoreForkedTests {
        override def otherModule: CoreForkedTests =  brokerstarter.test(otherTestGroup)
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.akkaSlf4j(akkaBundleRevision),
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
    object client extends CoreModule {
      override def description = "An Active MQ Connection factory as a service"
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
      override def testGroups: Map[String, Set[String]] = Map(
        "DefaultClientActivatorSpec" -> Set("blended.activemq.client.internal.DefaultClientActivatorSpec"),
        "SlowRoundtripSpec" -> Set("blended.activemq.client.internal.SlowRoundtripSpec"),
        "FailingClientActivatorSpec" -> Set("blended.activemq.client.internal.FailingClientActivatorSpec"),
        "RoundtripConnectionVerifierSpec" -> Set("blended.activemq.client.internal.RoundtripConnectionVerifierSpec")
      )
      object test extends Cross[Test](crossTestGroups: _*)
      class Test(override val testGroup: String) extends CoreForkedTests {
        override def otherModule: CoreForkedTests = client.test(otherTestGroup)
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.akkaSlf4j(akkaBundleRevision),
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

  object akka extends CoreModule {
    override def description = "Provide OSGi services and API to use Actors in OSGi bundles with a shared ActorSystem."

    override def ivyDeps = T{ super.ivyDeps() ++ Agg(
      deps.orgOsgi,
      deps.akkaActor(akkaBundleRevision),
      deps.akkaStream(akkaBundleRevision),
      deps.domino
    )}

    override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
      blended.util.logging,
      blended.container.context.api,
      blended.domino
    )

    override def essentialImportPackage : Seq[String] = super.essentialImportPackage ++ Seq(
      """akka.stream;version="[2.6,3)"""",
      """akka.stream.serialization;version="[2.6,3)"""",
    )

    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Some(s"${blendedModule}.internal.BlendedAkkaActivator")
    )}

    object test extends CoreTests {
      override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
        blended.testsupport,
        blended.testsupport.pojosr
      )
    }

    object http extends CoreModule {
      override def description = "Provide Akka HTTP support"
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        deps.domino,
        deps.akkaStream(akkaBundleRevision),
        deps.akkaHttp(akkaBundleRevision),
        deps.akkaHttpCore(akkaBundleRevision)
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
      object test extends CoreTests {

        override def forkArgs = T{ super.forkArgs() ++ Seq("-Dsun.net.client.defaultReadTimeout=3000")}

        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.akkaTestkit,
          deps.akkaSlf4j(akkaBundleRevision),
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

      object jmsqueue extends CoreModule {
        override def description = "Provide a simple REST interface to consume messages from JMS Queues"
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
        object test extends CoreTests {
          override def ivyDeps = T{ super.ivyDeps() ++ Agg(
            deps.sttp,
            deps.sttpAkka,
            deps.akkaSlf4j(akkaBundleRevision),
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

      object proxy extends CoreModule {
        override def description = "Provide Akka HTTP Proxy support"
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.domino,
          deps.akkaStream(akkaBundleRevision),
          deps.akkaHttp(akkaBundleRevision),
          deps.akkaActor(akkaBundleRevision)
        )}
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.domino,
          blended.container.context.api,
          blended.akka,
          blended.akka.http,
          blended.util,
          blended.util.logging
        )
        object test extends CoreTests {
          override def ivyDeps = T{ super.ivyDeps() ++ Agg(
            deps.akkaSlf4j(akkaBundleRevision),
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

      object restjms extends CoreModule {
        override def description = "Provide a simple REST interface to perform JMS request / reply operations"
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.domino,
          deps.akkaStream(akkaBundleRevision),
          deps.akkaHttp(akkaBundleRevision),
          deps.akkaActor(akkaBundleRevision),
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
        class Test(override val testGroup: String) extends CoreForkedTests {
          override def otherModule: CoreForkedTests = restjms.test(otherTestGroup)
          override def ivyDeps = T{ super.ivyDeps() ++ Agg(
            deps.sttp,
            deps.sttpAkka,
            deps.activeMqBroker,
            deps.activeMqClient,
            deps.springBeans,
            deps.springContext,
            deps.akkaSlf4j(akkaBundleRevision),
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
        object helloworld extends CoreModule {
          override def description = "A sample Akka HTTP bases HTTP endpoint for the blended container"
          override def millSourcePath: Path = baseDir / "blended.samples" / blendedModule
          override def ivyDeps = T{ super.ivyDeps() ++ Agg(
            deps.domino,
            deps.orgOsgi,
            deps.orgOsgiCompendium,
            deps.slf4j
          )}
          override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
            blended.akka,
            blended.akka.http
          )
          override def osgiHeaders = T { super.osgiHeaders().copy(
            `Bundle-Activator` = Option(s"$blendedModule.internal.HelloworldActivator")
          )}
          object test extends CoreTests {
            override def ivyDeps = T{ super.ivyDeps() ++ Agg(
              deps.slf4jLog4j12,
              deps.akkaStreamTestkit,
              deps.akkaHttpTestkit
            )}
          }
        }
      }
    }

    object logging extends CoreModule {
      override def description = "Redirect Akka Logging to the Blended logging framework"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.akkaActor(akkaBundleRevision)
      )}

      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.util.logging
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Private-Package` = Seq(blendedModule),
        `Fragment-Host` = Some(s"${deps.blendedOrg}.akka.actor_${scalaBinVersion()}")
      )}
    }
  }

  object container extends Module {

    object context extends Module {

      object api extends CoreModule {
        override def description = "The API for the Container Context and Identifier Service"
        override def ivyDeps = Agg(
          deps.typesafeConfig
        )

        override def essentialImportPackage: Seq[String] = Seq("blended.launcher.runtime;resolution:=optional")
        override def moduleDeps = Seq(
          blended.util.logging,
          blended.security.crypto
        )
        object test extends CoreTests {
          override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
            blended.testsupport
          )
        }
      }

      object impl extends CoreModule {
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
        object test extends CoreTests {
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

  object domino extends CoreModule {
    override def description = "Blended Domino extension for new Capsule scopes"
    override def ivyDeps = Agg(
      deps.typesafeConfig,
      deps.domino
    )
    override def moduleDeps = Seq(
      blended.util.logging,
      blended.container.context.api
    )
    object test extends CoreTests
  }

  object file extends CoreModule {
    override def description = "Bundle to define a customizable Filedrop / Filepoll API"
    override def moduleDeps = super.moduleDeps ++ Seq(
      blended.akka,
      blended.jms.utils,
      blended.streams
    )
    object test extends CoreTests {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.commonsIo,
        deps.activeMqBroker,
        deps.activeMqKahadbStore,
        deps.akkaTestkit,
        deps.akkaSlf4j(akkaBundleRevision)
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
    object login extends CoreModule {
      override def description = "Adding required imports to the hawtio war bundle"
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

  object itest extends Module {
    object runner extends CoreModule {
      override def description = "API for long running integration tests"
      override def osgiHeaders : T[OsgiHeaders] = T { super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.TestRunnerActivator")
      )}

      override def ivyDeps : Target[Loose.Agg[Dep]] = T { super.ivyDeps() ++ Agg(
        deps.domino
      )}

      override def moduleDeps : Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.akka,
        blended.domino,
        blended.jmx,
        blended.util.logging,
        blended.util
      )

      object test extends CoreTests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.akkaSlf4j(akkaBundleRevision)
        )}

        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.itestsupport,
          blended.testsupport,
          blended.testsupport.pojosr
        )
      }
    }
  }

  object jetty extends Module {
    object boot extends CoreModule {
      override def description = "Bundle wrapping the original jetty boot bundle to dynamically provide SSL Context via OSGI services"
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
    object bridge extends CoreModule {
      override def description = "A generic JMS bridge to connect the local JMS broker to en external JMS"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.akkaActor(akkaBundleRevision),
        deps.akkaStream(akkaBundleRevision),
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
      class Test(override val testGroup: String) extends CoreForkedTests {
        override def otherModule: CoreForkedTests =  bridge.test(otherTestGroup)
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.akkaSlf4j(akkaBundleRevision),
          deps.activeMqBroker,
          deps.scalacheck,
          deps.scalatestplusScalacheck,
          deps.springCore,
          deps.springBeans,
          deps.springContext,
          deps.springExpression
        )}
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.activemq.brokerstarter,
          blended.testsupport,
          blended.testsupport.pojosr,
          blended.streams.testsupport
        )
      }
    }
    object utils extends CoreModule {
      override def description = "A bundle to provide a ConnectionFactory wrapper that monitors a single connection and is able to monitor the connection via an active ping."
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
      object test extends CoreTests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.akkaSlf4j(akkaBundleRevision),
          deps.akkaStream(akkaBundleRevision),
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

  object jmx extends CoreJvmModule {
    override def description = "Helper bundle to expose the platform's MBeanServer as OSGI Service."
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
    object test extends CoreTests {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.scalacheck,
        deps.scalatestplusScalacheck
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport,
        blended.testsupport.pojosr
      )
    }
    object js extends CoreJs {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.js.prickle,
        deps.js.scalacheck
      )}

      object test extends super.BlendedJsTests
    }
  }

  object jolokia extends CoreModule {
    override def description = "Provide an Actor based Jolokia Client to access JMX resources of a container via REST"
    override def ivyDeps = super.ivyDeps() ++ Agg(
      deps.sprayJson,
      deps.jsonLenses,
      deps.slf4j,
      deps.sttp
    )
    override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
      blended.akka
    )
    object test extends CoreTests {
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

  object launcher extends CoreModule {
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

    object dist extends DistModule with CoreCoursierModule {
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

    object test extends CoreTests {
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport
      )
      def osgiFrameworkIvyDeps: Map[String, Dep] = Map(
        "Felix 5.0.0" -> ivy"org.apache.felix:org.apache.felix.framework:5.0.0",
        "Felix 5.6.10" -> ivy"org.apache.felix:org.apache.felix.framework:5.6.10",
        "Felix 6.0.3" -> ivy"org.apache.felix:org.apache.felix.framework:6.0.3",
        "Eclipse OSGi 3.10.0.v20140606-1445" -> ivy"org.eclipse.birt.runtime:org.eclipse.osgi:3.10.0.v20140606-1445",
        "Eclipse OSGi 3.10.100.v20150529-1857" -> ivy"org.osgi:org.eclipse.osgi:3.10.100.v20150529-1857",
        "Eclipse OSGi 3.12.50" -> ivy"org.eclipse.platform:org.eclipse.osgi:3.12.50",
        "Eclipse OSGi 3.15.200" -> ivy"org.eclipse.platform:org.eclipse.osgi:3.15.200"
      )
      def resolvedOsgiFrameworks = T{
        Target.traverse(osgiFrameworkIvyDeps.toSeq){ case (name, dep) =>
          T.task { name -> resolveDeps(T.task { Agg(dep) })().toSeq.head }
        }().toMap
      }
      override def generatedSources: Target[Seq[PathRef]] = T{ super.generatedSources() ++ {
        val file = T.dest / "blended" / "launcher" / "test_generated" / "generated.scala"
        val body =
          s"""
            |package blended.launcher.test_generated
            |
            |/** Generated with mill: The frameworks to use in the tests.
            | *  See [[blended.launcher.OsgiFrameworksTest]].
            | */
            |object TestOsgiFrameworks {
            |  val frameworks: Map[String, String] = ${
              resolvedOsgiFrameworks().map { case (name, file) => s""""${name}" -> "${file.path}"""" }
                .mkString("Map(\n    ", ",\n    ", "\n  )")
              }
            |}
            |
            |object JvmLauncherTest {
            |  val classpath: Seq[String] = ${
              blended.launcher.scoverage.runClasspath().map(ref => s""""${ref.path}"""")
                .mkString("Seq(\n    ", ",\n    ", "\n  )")
              }
            |}
            |""".stripMargin
        os.write(file, body, createFolders = true)
        Seq(PathRef(T.dest))
      }}
    }
  }

  object mgmt extends Module {
    object agent extends CoreModule {
      override def description = "Bundle to regularly report monitoring information to a central container hosting the container registry"
      override def ivyDeps = super.ivyDeps() ++ Agg(
        deps.orgOsgi,
        deps.akkaHttp(akkaBundleRevision),
        deps.akkaStream(akkaBundleRevision)
      )
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.akka,
        blended.updater.config,
        blended.util.logging,
        blended.prickle.akka.http
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.MgmtAgentActivator")
      )}
      object test extends CoreTests {
        override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
          blended.testsupport.pojosr
        )
      }
    }
    object mock extends CoreModule {
      override def description = "Mock server to simulate a larger network of blended containers for UI testing"
      override def ivyDeps = super.ivyDeps() ++ Agg(
        deps.cmdOption,
        deps.akkaActor(akkaBundleRevision),
        deps.logbackClassic
      )
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.mgmt.base,
        blended.mgmt.agent,
        blended.container.context.impl,
        blended.util.logging
      )
      object test extends CoreTests
    }
    object repo extends CoreModule {
      override def description = "File Artifact Repository"
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.domino,
        blended.updater.config,
        blended.util.logging,
        blended.mgmt.base
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.ArtifactRepoActivator")
      )}
      object test extends CoreTests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.lambdaTest,
          deps.akkaTestkit,
          deps.akkaSlf4j(akkaBundleRevision)
        )}
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport
        )
      }

      object rest extends CoreModule {
        override def description = "File Artifact Repository REST Service"
        override def ivyDeps = super.ivyDeps() ++ Agg(
          deps.akkaHttp(akkaBundleRevision)
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
        object test extends CoreTests {
          override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
            blended.testsupport.pojosr
          )
        }
      }
    }
    object rest extends CoreModule {
      override def description = "REST interface to accept POST's from distributed containers. These will be delegated to the container registry"
      override def ivyDeps = super.ivyDeps() ++ Agg(
        deps.akkaActor(akkaBundleRevision),
        deps.domino,
        deps.akkaHttp(akkaBundleRevision),
        deps.akkaHttpCore(akkaBundleRevision),
        deps.akkaStream(akkaBundleRevision)
      )
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.util.logging,
        blended.akka.http,
        blended.persistence,
        blended.security.akka.http,
        blended.akka,
        blended.prickle.akka.http,
        blended.mgmt.repo
      )
      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.MgmtRestActivator")
      )}
      object test extends CoreTests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.akkaSlf4j(akkaBundleRevision),
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
    object base extends CoreModule {
      override def description = "Shared classes for management and reporting facility"
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.domino,
        blended.container.context.api,
        blended.util,
        blended.util.logging
      )
      object test extends CoreTests {
        override def moduleDeps = super.moduleDeps ++ Seq(
          blended.testsupport,
          blended.testsupport.pojosr
        )

        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.lambdaTest
        )}
      }
    }
    object service extends Module {
      object jmx extends CoreModule {
        override def description = "A JMX based Service Info Collector"
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.domino,
          blended.util.logging,
          blended.akka,
          blended.updater.config
        )
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Bundle-Activator` = Some(s"${blendedModule}.internal.ServiceJmxActivator")
        )}
        object test extends CoreTests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            deps.akkaTestkit
          )}
        }
      }
    }
  }
  object persistence extends CoreModule {
    override def description = "Provide a technology agnostic persistence API with pluggable Data Objects defined in other bundles"
    override def ivyDeps = super.ivyDeps() ++ Agg(
      deps.slf4j,
      deps.domino
    )
    override def moduleDeps = super.moduleDeps ++ Seq(
      blended.akka
    )
    object test extends CoreTests {
      override def ivyDeps = super.ivyDeps() ++ Agg(
        deps.mockitoAll,
        deps.slf4jLog4j12
      )
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport
      )
    }

    object h2 extends CoreModule {
      override def description = "Implement a persistence backend with H2 JDBC database"
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
      object test extends CoreTests {
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

  object prickle extends CoreJvmModule {
    override def description = "OSGi package for Prickle and mircojson"
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
      object http extends CoreModule {
        override def description = "Define some convenience to use Prickle with Akka HTTP"
        override def ivyDeps = super.ivyDeps() ++ Agg(
          deps.akkaHttpCore(akkaBundleRevision),
          deps.akkaHttp(akkaBundleRevision),
          deps.akkaStream(akkaBundleRevision),
          deps.prickle
        )
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.util.logging
        )
        object test extends CoreTests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            deps.akkaStreamTestkit,
            deps.akkaHttpTestkit
          )}
        }
      }
    }
  }

  object security extends CoreJvmModule {
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
    object test extends BlendedJvmTests {
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
    object js extends CoreJs {
      override def ivyDeps = T { super.ivyDeps() ++ Agg(
        deps.js.prickle
      )}
      object test extends super.BlendedJsTests
    }

    object akka extends Module {
      object http extends CoreModule {
        override def description = "Some security aware Akka HTTP routes for the blended container"
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          deps.akkaHttp(akkaBundleRevision),
          deps.akkaHttpCore(akkaBundleRevision),
          deps.akkaStream(akkaBundleRevision),
          deps.orgOsgi,
          deps.orgOsgiCompendium,
          deps.slf4j
        )}
        override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
          blended.akka,
          blended.security,
          blended.util.logging
        )
        object test extends CoreTests {
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
      object api extends CoreModule {
        override def description = "API to provide the backend for a Login Service"
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
        object test extends CoreTests {
          override def moduleDeps = super.moduleDeps ++ Seq(
            blended.testsupport,
            blended.testsupport.pojosr
          )
        }
      }
      object impl extends CoreModule {
        override def description = "Implementation of the Login backend"
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
        object test extends CoreTests {
          override def moduleDeps = super.moduleDeps ++ Seq(
            blended.testsupport,
            blended.testsupport.pojosr
          )
        }
      }
      object rest extends CoreModule {
        override def description = "A REST service providing login services and web token management"
        override def ivyDeps = super.ivyDeps() ++ Agg(
          deps.akkaHttp(akkaBundleRevision),
          deps.akkaHttpCore(akkaBundleRevision)
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
        class Test(override val testGroup: String) extends CoreForkedTests {
          override def otherModule: CoreForkedTests =  rest.test(otherTestGroup)
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

    object boot extends CoreModule {
      override def description = "A delegating login module for the blended container"
      override def compileIvyDeps: Target[Agg[Dep]] = Agg(
        deps.orgOsgi
      )
      override def osgiHeaders = T { super.osgiHeaders().copy(
        `Fragment-Host` = Some("system.bundle;extension:=framework"),
        `Import-Package` = Seq("")
      )}
    }

    object crypto extends CoreModule {
      override def description = "Provides classes and mainline for encrypting / decrypting arbitrary Strings"
      override def ivyDeps = Agg(
        deps.cmdOption
      )
      override def essentialImportPackage: Seq[String] = Seq("de.tototec.cmdoption;resolution:=optional")
      object test extends CoreTests {
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

    object ssl extends CoreModule {
      override def description = "Bundle to provide simple Server Certificate Management"
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
      class Test(override val testGroup: String) extends CoreForkedTests {
        override def forkArgs: Target[Seq[String]] = T{ super.forkArgs() ++ Seq(
          s"-Djava.security.properties=${copiedResources().path.toIO.getPath()}/container/security.properties"
        )}
        override def otherModule: CoreForkedTests =  ssl.test(otherTestGroup)
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

    object scep extends CoreModule {
      override def description = "Bundle to manage the container certificate via SCEP."
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
      object test extends CoreTests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.logbackClassic
        )}
        override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
          testsupport,
          testsupport.pojosr
        )
      }

      object standalone extends CoreModule {
        override def description = "Standalone client to manage certificates via SCEP"
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
        object test extends CoreTests {
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

  object streams extends CoreModule {
    override def description = "Helper objects to work with Streams in blended integration flows."
    override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
      deps.akkaActor(akkaBundleRevision),
      deps.akkaStream(akkaBundleRevision),
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
      s"${blendedModule}.multiresult",
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
    class Test(override val testGroup: String) extends CoreForkedTests {
      override def otherModule: CoreForkedTests = streams.test(otherTestGroup)
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.akkaSlf4j(akkaBundleRevision),
        deps.commonsIo,
        deps.scalacheck,
        deps.scalatestplusScalacheck,
        deps.akkaTestkit,
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
    object dispatcher extends CoreModule {
      override def description = "A generic Dispatcher to support common integration routing."
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{super.ivyDeps() ++ Agg(
        deps.akkaActor(akkaBundleRevision),
        deps.akkaStream(akkaBundleRevision),
        deps.geronimoJms11Spec,
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
      class Test(override val testGroup: String) extends CoreForkedTests {
        override def otherModule: CoreForkedTests = dispatcher.test(otherTestGroup)
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.akkaTestkit,
          deps.akkaSlf4j(akkaBundleRevision),
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
    object testsupport extends CoreModule {
      override def description = "Some classes to make testing for streams a bit easier"
      override def ivyDeps = Agg(
        deps.scalacheck,
        deps.scalatest,
        deps.akkaTestkit,
        deps.akkaActor(akkaBundleRevision),
        deps.akkaStream(akkaBundleRevision),
        deps.logbackClassic
      )
      override def moduleDeps = Seq(
        blended.util.logging,
        blended.streams,
        blended.testsupport
      )

      object test extends CoreTests

    }
  }

  object testsupport extends CoreModule {
    override def description = "Some test helper classes"
    override def ivyDeps = Agg(
      deps.akkaActor(akkaBundleRevision),
      deps.akkaTestkit,
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
    object test extends CoreTests

    object pojosr extends CoreModule {
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
      object test extends CoreTests
    }
  }

  object updater extends CoreModule {
    override def description = "OSGi Updater"
    override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
      deps.orgOsgi,
      deps.domino,
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
    object test extends CoreTests {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.akkaTestkit,
        deps.felixFramework,
        deps.logbackClassic,
        deps.akkaSlf4j(akkaBundleRevision),
        deps.felixGogoRuntime,
        deps.felixGogoShell,
        deps.felixGogoCommand,
        deps.felixFileinstall,
        deps.mockitoAll
      )}
      override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
        blended.testsupport,
        blended.testsupport.pojosr,
        blended.akka
      )
    }

    object config extends CoreJvmModule {
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
      object test extends BlendedJvmTests {
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

      object js extends CoreJs {
        override def moduleDeps: Seq[PublishModule] = Seq(
          blended.security.js
        )
        object test extends BlendedJsTests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            deps.js.prickle
          )}
          override def moduleDeps = super.moduleDeps ++ Seq(
            blended.util.logging.js
          )
        }
      }
    }

    object tools extends CoreModule {
      override def description = "Configurations for Updater and Launcher"
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.typesafeConfig,
        deps.cmdOption
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.updater.config
      )
      object test extends CoreTests
    }

  }

  object util extends CoreModule {
    override def description = "Utility classes to use in other bundles"
    override def compileIvyDeps: Target[Agg[Dep]] = Agg(
      deps.akkaActor(akkaBundleRevision),
      deps.akkaSlf4j(akkaBundleRevision),
      deps.slf4j,
      deps.typesafeConfig
    )
    override def exportPackages : Seq[String] = super.exportPackages ++ Seq(
      s"${blendedModule}.arm",
      s"${blendedModule}.config",
      s"${blendedModule}.io"
    )
    object test extends CoreTests {
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        deps.akkaTestkit,
        deps.junit,
        deps.logbackClassic,
        deps.logbackCore
      )}
    }
    object logging extends CoreJvmModule {
      override def description = "Logging utility classes to use in other bundles"
      override def compileIvyDeps: Target[Agg[Dep]] = Agg(
        deps.slf4j
      )
      object test extends CoreTests

      object js extends CoreJs
    }
  }

  object websocket extends CoreJvmModule {
    override def description = "The web socket server module"
    override def ivyDeps = super.ivyDeps() ++ Agg(
      deps.akkaHttp(akkaBundleRevision),
      deps.akkaHttpCore(akkaBundleRevision)
    )
    override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
      blended.akka,
      blended.streams,
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
    object test extends BlendedJvmTests {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.akkaSlf4j(akkaBundleRevision),
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

    object js extends CoreJs {
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        deps.js.prickle
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.jmx.js
      )
      object test extends BlendedJsTests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          deps.js.scalacheck
        )}
      }
    }
  }

  object itestsupport extends CoreModule {
    override def description = "Integration test helper classes"
    override def ivyDeps = T { super.ivyDeps() ++  Agg(
        deps.akkaSlf4j(akkaBundleRevision),
        deps.activeMqBroker,
        deps.akkaActor(akkaBundleRevision),
        deps.akkaStream(akkaBundleRevision),
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
    object test extends CoreTests {
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
        }.iterator.next()

        super.forkArgs() ++ Seq(
          s"-javaagent:${jolokiaAgent.path.toIO.getAbsolutePath()}=port=0,host=localhost"
        )
      }
    }
  }

  object features extends BlendedFeatureJar with CorePublishModule with CoreCoursierModule {

    override def baseDir : os.Path = projectDir
    override type ProjectDeps = CoreDependencies
    override def deps : ProjectDeps = CoreDependencies.Deps_2_13
    override def scalaVersion = crossScalaVersion

    def baseName : String = "blended.features"

    def repoUrl : T[String] = T { s"mvn:${deps.blendedOrg}:${artifactId()}:${publishVersion()}"}

    val coreDep : String => String => Dep = version => name =>
      Dep(
        org = deps.blendedOrg,
        name = name,
        version = version,
        cross = CrossVersion.Binary(false)
      )

    def selfDep : T[Dep] = T {
      Dep(
        org = deps.blendedOrg,
        name = artifactName(),
        version = publishVersion(),
        cross = CrossVersion.Binary(false)
      )
    }

    def coreDep(m : CoreModule) = T.task{

      Dep(
        org = m.artifactMetadata().group,
        name = m.artifactName(),
        version = m.publishVersion(),
        cross = CrossVersion.Binary(false)
      )
    }

    def activemq : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".activemq",
        features = Seq.empty,
        bundles = Seq(
          FeatureBundle(deps.ariesProxyApi),
          FeatureBundle(deps.ariesBlueprintApi),
          FeatureBundle(deps.ariesBlueprintCore),
          FeatureBundle(deps.geronimoAnnotation),
          FeatureBundle(deps.geronimoJms11Spec),
          FeatureBundle(deps.geronimoJ2eeMgmtSpec),
          FeatureBundle(deps.servicemixStaxApi),
          FeatureBundle(deps.activeMqOsgi),
          FeatureBundle(coreDep(blended.activemq.brokerstarter)(), 4, true),
          FeatureBundle(coreDep(blended.jms.utils)()),
          FeatureBundle(deps.springJms)
        )
      )
    }

    def akkaHttpBase : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".akka.http.base",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(baseCommon()).map(_.name))
        ),
        bundles = Seq(
          FeatureBundle(deps.akkaParsing(akkaBundleRevision)),
          FeatureBundle(deps.akkaHttp(akkaBundleRevision)),
          FeatureBundle(deps.akkaHttpCore(akkaBundleRevision)),
          FeatureBundle(coreDep(blended.akka.http)(), 4, true),
          FeatureBundle(coreDep(blended.prickle.akka.http)()),
          FeatureBundle(coreDep(blended.security.akka.http)())
        )
      )
    }

    def akkaHttpModules : T[Feature] = T{
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".akka.http.modules",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(akkaHttpBase(), spring()).map(_.name))
        ),
        bundles = Seq(
          FeatureBundle(coreDep(blended.akka.http.proxy)()),
          FeatureBundle(coreDep(blended.akka.http.restjms)()),
          FeatureBundle(coreDep(blended.akka.http.jmsqueue)())
        )
      )
    }

    def baseCommon : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".base.common",
        features = Seq.empty,
        bundles = Seq(
          FeatureBundle(coreDep(blended.security.boot)()),
          FeatureBundle(deps.asmAll, 4, true),
          FeatureBundle(coreDep(blended.updater)(), 4, true),
          FeatureBundle(coreDep(blended.updater.config)(), 4, true),
          FeatureBundle(deps.scalaReflect(scalaVersion())),
          FeatureBundle(deps.scalaLibrary(scalaVersion())),
          FeatureBundle(deps.scalaXml),
          FeatureBundle(deps.scalaCompatJava8),
          FeatureBundle(deps.scalaParser),
          FeatureBundle(coreDep(blended.akka)(), 4, true),
          FeatureBundle(coreDep(blended.util.logging)(), 4, true),
          FeatureBundle(coreDep(blended.util)(), 4, true),
          FeatureBundle(deps.springCore),
          FeatureBundle(deps.springExpression),
          FeatureBundle(coreDep(blended.container.context.api)()),
          FeatureBundle(coreDep(blended.container.context.impl)(), 4, true),
          FeatureBundle(coreDep(blended.security.crypto)()),
          FeatureBundle(deps.felixConfigAdmin, 4, true),
          FeatureBundle(deps.felixEventAdmin, 4, true),
          FeatureBundle(deps.felixFileinstall, 4, true),
          FeatureBundle(deps.felixMetatype, 4, true),
          FeatureBundle(deps.typesafeConfig),
          FeatureBundle(deps.typesafeSslConfigCore),
          FeatureBundle(deps.reactiveStreams),
          FeatureBundle(deps.akkaActor(akkaBundleRevision)),
          FeatureBundle(coreDep(blended.akka.logging)()),
          FeatureBundle(deps.akkaProtobuf(akkaBundleRevision)),
          FeatureBundle(deps.akkaProtobufV3(akkaBundleRevision)),
          FeatureBundle(deps.akkaStream(akkaBundleRevision)),
          FeatureBundle(deps.domino),
          FeatureBundle(coreDep(blended.domino)()),
          FeatureBundle(coreDep(blended.mgmt.base)(), 4, true),
          FeatureBundle(coreDep(blended.prickle)()),
          FeatureBundle(coreDep(blended.mgmt.service.jmx)())
        )
      )
    }

    def baseEquinox : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + "base.equinox",
        features = Seq.empty,
        bundles = Seq(
          FeatureBundle(deps.eclipseOsgi, 0, true),
          FeatureBundle(deps.eclipseEquinoxConsole, 1, true),
          FeatureBundle(deps.jline, 1, false),
          FeatureBundle(deps.jlineBuiltins, 1, false),
          FeatureBundle(deps.felixGogoJline, 1, true),
          FeatureBundle(deps.felixGogoRuntime, 1, true),
          FeatureBundle(deps.felixGogoShell, 1, true),
          FeatureBundle(deps.felixGogoCommand, 1, true),
          FeatureBundle(deps.felixShellRemote, 1, true)
        )
      )
    }

    def baseFelix : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".base.felix",
        features = Seq.empty,
        bundles = Seq(
          FeatureBundle(deps.felixFramework, 0, true),
          FeatureBundle(deps.orgOsgiCompendium, 1, true),
          FeatureBundle(deps.jline, 1, false),
          FeatureBundle(deps.jlineBuiltins, 1, false),
          FeatureBundle(deps.felixGogoJline, 1, true),
          FeatureBundle(deps.felixGogoRuntime, 1, true),
          FeatureBundle(deps.felixGogoShell, 1, true),
          FeatureBundle(deps.felixGogoCommand, 1, true),
          FeatureBundle(deps.felixShellRemote, 1, true)
        )
      )
    }

    def commons : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".commons",
        features = Seq.empty,
        bundles = Seq(
          FeatureBundle(deps.ariesUtil),
          FeatureBundle(deps.ariesJmxApi),
          FeatureBundle(deps.ariesJmxCore, 4, true),
          FeatureBundle(coreDep(blended.jmx)(), 4, true),
          FeatureBundle(deps.commonsCollections),
          FeatureBundle(deps.commonsDiscovery),
          FeatureBundle(deps.commonsLang3),
          FeatureBundle(deps.commonsPool2),
          FeatureBundle(deps.commonsNet),
          FeatureBundle(deps.commonsExec),
          FeatureBundle(deps.commonsIo),
          FeatureBundle(deps.commonsCodec),
          FeatureBundle(deps.commonsHttpclient),
          FeatureBundle(deps.commonsBeanUtils)
        )
      )
    }

    def hawtio : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".hawtio",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(jetty()).map(_.name))
        ),
        bundles = Seq(
          FeatureBundle(deps.hawtioWeb, 10, true),
          FeatureBundle(coreDep(blended.hawtio.login)())
        )
      )
    }

    def jetty : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".jetty",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(baseCommon()).map(_.name))
        ),
        bundles = Seq(
          FeatureBundle(deps.activationApi),
          FeatureBundle(deps.javaxServlet31),
          FeatureBundle(deps.javaxMail),
          FeatureBundle(deps.geronimoAnnotation),
          FeatureBundle(deps.geronimoJaspic),
          FeatureBundle(deps.jettyUtil),
          FeatureBundle(deps.jettyHttp),
          FeatureBundle(deps.jettyIo),
          FeatureBundle(deps.jettyJmx),
          FeatureBundle(deps.jettySecurity),
          FeatureBundle(deps.jettyServlet),
          FeatureBundle(deps.jettyServer),
          FeatureBundle(deps.jettyWebapp),
          FeatureBundle(deps.jettyDeploy),
          FeatureBundle(deps.jettyXml),
          FeatureBundle(deps.equinoxServlet),
          FeatureBundle(deps.felixHttpApi),
          FeatureBundle(coreDep(blended.jetty.boot)(), 4, true),
          FeatureBundle(deps.jettyHttpService, 4, true)
        )
      )
    }

    def jolokia : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".jolokia",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(jetty()).map(_.name))
        ),
        bundles = Seq(
          FeatureBundle(deps.jolokiaOsgi, 4, true)
        )
      )
    }

    def login : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".login",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(
            baseCommon(), akkaHttpBase(), security(), persistence(), streams()
          ).map(_.name))
        ),
        bundles = Seq(
          // Required for Json Web Token
          FeatureBundle(deps.jacksonAnnotations),
          FeatureBundle(deps.jacksonCore),
          FeatureBundle(deps.jacksonBind),
          FeatureBundle(deps.jjwt),
          // Required for Login API
          FeatureBundle(coreDep(blended.security.login.api)()),
          FeatureBundle(coreDep(blended.security.login.impl)(), 4, true),
          FeatureBundle(coreDep(blended.websocket)(), 4, true),
          FeatureBundle(coreDep(blended.security.login.rest)(), 4, true)
        )
      )
    }

    def mgmtClient : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".mgmt.client",
        features = Seq.empty,
        bundles = Seq(
          FeatureBundle(coreDep(blended.mgmt.agent)(), 4, true)
        )
      )
    }

    def mgmtServer : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".mgmt.server",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(
            baseCommon(), akkaHttpBase(), security(), ssl(), spring(),
            persistence(), login(), streams()
          ).map(_.name))
        ),
        bundles = Seq(
          FeatureBundle(coreDep(blended.mgmt.rest)(), 4, true),
          FeatureBundle(coreDep(blended.mgmt.repo)(), 4, true),
          FeatureBundle(coreDep(blended.mgmt.repo.rest)(), 4, true),
          FeatureBundle(deps.concurrentLinkedHashMapLru),
          FeatureBundle(deps.jsr305)
          //FeatureBundle(ivy"${deps.blendedOrg}::blended.mgmt.ui.mgmtApp.webBundle:$blendedUiVersion", 4, true)
        )
      )
    }

    def persistence : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".persistence",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(baseCommon()).map(_.name))
        ),
        bundles = Seq(
          FeatureBundle(coreDep(blended.persistence)()),
          FeatureBundle(coreDep(blended.persistence.h2)(), 4, true),
          // for Blended.persistenceH2
          FeatureBundle(deps.h2),
          // for Blended.persistenceH2
          FeatureBundle(deps.hikaricp),
          // for Blended.persistenceH2
          FeatureBundle(deps.liquibase),
          // for deps.liquibase
          FeatureBundle(deps.snakeyaml)
        )
      )
    }

    def samples : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".samples",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(akkaHttpBase(), activemq(), streams()).map(_.name))
        ),
        bundles = Seq(
          FeatureBundle(coreDep(blended.jms.bridge)(), 4, true),
          FeatureBundle(coreDep(blended.streams.dispatcher)(), 4, true),
          FeatureBundle(coreDep(blended.activemq.client)(), 4, true),
          FeatureBundle(coreDep(blended.file)()),
          FeatureBundle(coreDep(blended.akka.http.sample.helloworld)(), 4, true)
        )
      )
    }

    def security : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".security",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(baseCommon()).map(_.name))
        ),
        bundles = Seq(
          FeatureBundle(coreDep(blended.security)(), 4, true)
        )
      )
    }

    def spring : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".spring",
        features = Seq.empty,
        bundles = Seq(
          FeatureBundle(deps.aopAlliance),
          FeatureBundle(deps.springBeans),
          FeatureBundle(deps.springAop),
          FeatureBundle(deps.springContext),
          FeatureBundle(deps.springContextSupport),
          FeatureBundle(deps.springJdbc),
          FeatureBundle(deps.springTx)
        )
      )
    }

    def ssl : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".ssl",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(baseCommon()).map(_.name))
        ),
        bundles = Seq(
          FeatureBundle(deps.javaxServlet31),
          FeatureBundle(coreDep(blended.security.scep)(), 4, true),
          FeatureBundle(coreDep(blended.security.ssl)(), 4, true)
        )
      )
    }

    def streams : T[Feature] = T {
      Feature(
        repoUrl = repoUrl(),
        name = baseName + ".streams",
        features = Seq(
          FeatureRef(dependency = selfDep(), names = Seq(baseCommon()).map(_.name))
        ),
        bundles = Seq(
          FeatureBundle(deps.geronimoJms11Spec),
          FeatureBundle(coreDep(blended.jms.utils)()),
          FeatureBundle(coreDep(blended.streams)(), 4, true)
        )
      )
    }

    override def features() : define.Command[Seq[Feature]] = T.command {
      Seq(
        activemq(), akkaHttpBase(), akkaHttpModules(),
        baseCommon(), baseEquinox(), baseFelix(),
        commons(), hawtio(), jetty(), jolokia(),
        login(), mgmtClient(), mgmtServer(), persistence(),
        samples(), security(), spring(), ssl(), streams()
      )
    }
  }
}

object scoverage extends ScoverageReport {
  override def scalaVersion = CoreDependencies.Deps_2_13.scalaVersion
  override def scoverageVersion = CoreDependencies.Deps_2_13.scoverageVersion
}
