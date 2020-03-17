import scala.util.Try

import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import mill.api.Loose
import mill.{PathRef, _}
import mill.define.{Command, Sources, Target}
import mill.scalajslib.ScalaJSModule
import mill.scalajslib.api.ModuleKind
import mill.scalalib._
import mill.scalalib.publish._
import os.Path

// This import the mill-osgi plugin
import $ivy.`de.tototec::de.tobiasroeser.mill.osgi:0.2.0`
import de.tobiasroeser.mill.osgi._

/** Project directory. */
val baseDir: os.Path = build.millSourcePath

object Deps {

  // Versions
  val activeMqVersion = "5.15.6"
  val akkaVersion = "2.5.21"
  val akkaHttpVersion = "10.1.7"
  val camelVersion = "2.19.5"
  val dominoVersion = "1.1.3"
  val jettyVersion = "9.4.18.v20190429"
  val jolokiaVersion = "1.6.1"
  val microJsonVersion = "1.4"
  val parboiledVersion = "1.1.6"
  val prickleVersion = "1.1.14"
  val scalaJsVersion = "0.6.29"
  val scalaVersion = "2.12.8"
  val scalatestVersion = "3.0.5"
  val scalaCheckVersion = "1.14.0"
  val slf4jVersion = "1.7.25"
  val sprayVersion = "1.3.4"
  val springVersion = "4.3.12.RELEASE_1"

  val activeMqBroker = ivy"org.apache.activemq:activemq-broker:${activeMqVersion}"
  val activeMqClient = ivy"org.apache.activemq:activemq-client:${activeMqVersion}"
  val activeMqKahadbStore = ivy"org.apache.activemq:activemq-kahadb-store:${activeMqVersion}"
  val activeMqSpring = ivy"org.apache.activemq:activemq-spring:${activeMqVersion}"

  protected def akka(m: String) = ivy"com.typesafe.akka::akka-${m}:${akkaVersion}"

  protected def akkaHttpModule(m: String) = ivy"com.typesafe.akka::akka-${m}:${akkaHttpVersion}"

  val akkaActor = akka("actor")
  val akkaCamel = akka("camel")
  val akkaHttp = akkaHttpModule("http")
  val akkaHttpCore = akkaHttpModule("http-core")
  val akkaHttpTestkit = akkaHttpModule("http-testkit")
  val akkaOsgi = akka("osgi")
  val akkaParsing = akkaHttpModule("parsing")
  val akkaPersistence = akka("persistence")
  val akkaStream = akka("stream")
  val akkaStreamTestkit = akka("stream-testkit")
  val akkaTestkit = akka("testkit")
  val akkaSlf4j = akka("slf4j")

  val asciiRender = ivy"com.indvd00m.ascii.render:ascii-render:1.2.3"

  val bouncyCastleBcprov = ivy"org.bouncycastle:bcprov-jdk15on:1.60"
  val bouncyCastlePkix = ivy"org.bouncycastle:bcpkix-jdk15on:1.60"

  val camelCore = ivy"org.apache.camel:camel-core:${camelVersion}"
  val camelJms = ivy"org.apache.camel:camel-jms:${camelVersion}"

  val cmdOption = ivy"de.tototec:de.tototec.cmdoption:0.6.0"
  val commonsBeanUtils = ivy"commons-beanutils:commons-beanutils:1.9.3"
  val commonsCodec = ivy"commons-codec:commons-codec:1.11"
  val commonsDaemon = ivy"commons-daemon:commons-daemon:1.0.15"
  val commonsIo = ivy"commons-io:commons-io:2.6"
  val commonsLang2 = ivy"commons-lang:commons-lang:2.6"
  val concurrentLinkedHashMapLru = ivy"com.googlecode.concurrentlinkedhashmap:concurrentlinkedhashmap-lru:1.4.2"

  val domino = ivy"com.github.domino-osgi::domino:${dominoVersion}"

  val felixConnect = ivy"org.apache.felix:org.apache.felix.connect:0.1.0"
  val felixGogoCommand = ivy"org.apache.felix:org.apache.felix.gogo.command:1.1.0"
  val felixGogoJline = ivy"org.apache.felix:org.apache.felix.gogo.jline:1.1.4"
  val felixGogoShell = ivy"org.apache.felix:org.apache.felix.gogo.shell:1.1.2"
  val felixGogoRuntime = ivy"org.apache.felix:org.apache.felix.gogo.runtime:1.1.2"
  val felixFileinstall = ivy"org.apache.felix:org.apache.felix.fileinstall:3.4.2"
  val felixFramework = ivy"org.apache.felix:org.apache.felix.framework:6.0.2"

  val geronimoJms11Spec = ivy"org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1"

  val h2 = ivy"com.h2database:h2:1.4.197"
  val hikaricp = ivy"com.zaxxer:HikariCP:3.1.0"

  protected def jettyOsgi(n: String) = ivy"org.eclipse.jetty.osgi:jetty-${n}:${jettyVersion}"

  val jcip = ivy"net.jcip:jcip-annotations:1.0"
  val jclOverSlf4j = ivy"org.slf4j:jcl-over-slf4j:${slf4jVersion}"
  val jettyOsgiBoot = jettyOsgi("osgi-boot")
  val jjwt = ivy"io.jsonwebtoken:jjwt:0.7.0"
  val jms11Spec = ivy"org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1"
  val jolokiaJvm = ivy"org.jolokia:jolokia-jvm:${jolokiaVersion}"
  //    val jolokiaJvmAgent = jolokiaJvm.classifier("agent")
  val jscep = ivy"com.google.code.jscep:jscep:2.5.0"
  val jsonLenses = ivy"net.virtual-void::json-lenses:0.6.2"
  val julToSlf4j = ivy"org.slf4j:jul-to-slf4j:${slf4jVersion}"
  val junit = ivy"junit:junit:4.12"

  val lambdaTest = ivy"de.tototec:de.tobiasroeser.lambdatest:0.6.2"
  val levelDbJava = ivy"org.iq80.leveldb:leveldb:0.9"
  val levelDbJni = ivy"org.fusesource.leveldbjni:leveldbjni-all:1.8"
  val liquibase = ivy"org.liquibase:liquibase-core:3.6.1"
  /** Only for use in test that also runs in JS */
  val log4s = ivy"org.log4s::log4s:1.6.1"
  val logbackCore = ivy"ch.qos.logback:logback-core:1.2.3"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.2.3"

  val microjson = ivy"com.github.benhutchison::microjson:${microJsonVersion}"
  val mimepull = ivy"org.jvnet.mimepull:mimepull:1.9.5"
  val mockitoAll = ivy"org.mockito:mockito-all:1.9.5"

  val orgOsgi = ivy"org.osgi:org.osgi.core:6.0.0"
  val orgOsgiCompendium = ivy"org.osgi:org.osgi.compendium:5.0.0"
  val osLib = ivy"com.lihaoyi::os-lib:0.4.2"

  val parboiledCore = ivy"org.parboiled:parboiled-core:${parboiledVersion}"
  val parboiledScala = ivy"org.parboiled::parboiled-scala:${parboiledVersion}"
  val prickle = ivy"com.github.benhutchison::prickle:${prickleVersion}"

  // SCALA
  val scalaLibrary = ivy"org.scala-lang:scala-library:${scalaVersion}"
  val scalaReflect = ivy"org.scala-lang:scala-reflect:${scalaVersion}"
  val scalaParser = ivy"org.scala-lang.modules::scala-parser-combinators:1.1.1"
  val scalaXml = ivy"org.scala-lang.modules::scala-xml:1.1.0"

  val scalacheck = ivy"org.scalacheck::scalacheck:1.14.0"
  val scalatest = ivy"org.scalatest::scalatest:${scalatestVersion}"
  val shapeless = ivy"com.chuusai::shapeless:1.2.4"
  val slf4j = ivy"org.slf4j:slf4j-api:${slf4jVersion}"
  val slf4jLog4j12 = ivy"org.slf4j:slf4j-log4j12:${slf4jVersion}"
  val snakeyaml = ivy"org.yaml:snakeyaml:1.18"
  val sprayJson = ivy"io.spray::spray-json:${sprayVersion}"

  //  protected def spring(n: String) = ivy"org.springframework" % s"spring-${n}" % springVersion
  protected def spring(n: String) = ivy"org.apache.servicemix.bundles:org.apache.servicemix.bundles.spring-${n}:${springVersion}"

  val springBeans = spring("beans")
  val springAop = spring("aop")
  val springContext = spring("context")
  val springContextSupport = spring("context-support")
  val springExpression = spring("expression")
  val springCore = spring("core")
  val springJdbc = spring("jdbc")
  val springJms = spring("jms")
  val springTx = spring("tx")

  val sttp = ivy"com.softwaremill.sttp::core:1.3.0"
  val sttpAkka = ivy"com.softwaremill.sttp::akka-http-backend:1.3.0"

  val travesty = ivy"net.mikolak::travesty:0.9.1_2.5.17"

  val typesafeConfig = ivy"com.typesafe:config:1.3.3"
  val typesafeSslConfigCore = ivy"com.typesafe::ssl-config-core:0.3.6"

  object js {
    val prickle = ivy"com.github.benhutchison::prickle::${prickleVersion}"
    val scalatest = ivy"org.scalatest::scalatest::${scalatestVersion}"
    val scalacheck = ivy"org.scalacheck::scalacheck::${scalaCheckVersion}"
  }

}

trait BlendedModule extends SbtModule with PublishModule with OsgiBundleModule {
  /** The blended module name. */
  def blendedModule: String = millModuleSegments.parts.mkString(".")
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
      organization = "Way of Quality",
      url = "https://github.com/woq-blended",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("woq-blended", "blended"),
      developers = Seq(
        Developer("atooni", "Andreas Gies", "https://github.com/atooni"),
        Developer("lefou", "Tobias Roeser", "https://github.com/lefou")
      )
    )
  }
  override def scalacOptions = Seq("-deprecation")

  trait Tests extends super.Tests {
    override def ivyDeps = T{ super.ivyDeps() ++ Agg(
      Deps.scalatest
    )}
    override def testFrameworks = Seq("org.scalatest.tools.Framework")
    /** Empty, we use [[testResources]] instead to model sbt behavior. */
    override def resources = T.sources { Seq.empty[PathRef] }
    def copyResources: Sources = T.sources(
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
           |      <pattern>%date %level [%thread] %logger{36} %msg%n</pattern>
           |    </encoder>
           |  </appender>
           |
           |  <root level="debug">
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
    def testResources(): Command[PathRef] = T.command {
      val dest = T.ctx().dest
      copyResources().foreach { p =>
        if(os.exists(p.path)) {
          os.list(p.path).foreach { p1 =>
            os.copy.into(p1, dest)
          }
        }
      }
      PathRef(dest)
    }
    override def runClasspath: Target[Seq[PathRef]] = T{ super.runClasspath() ++ Seq(logResources(), testResources()()) }
    override def forkArgs: Target[Seq[String]] = T{ super.forkArgs() ++ Seq(
      s"-DprojectTestOutput=${testResources()().path.toIO.getPath()}"
    )}
  }

  /** Show all compiled classes. */
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
      super.sources() ++ Seq(PathRef(millSourcePath / os.up / 'shared / 'src / 'test / 'scala))
    }
    override def copyResources = T.sources { super.resources() ++ Seq(
      PathRef(millSourcePath / os.up / 'shared / 'src / 'test / 'resources),
      PathRef(millSourcePath / os.up / 'shared / 'src / 'test / 'binaryResources)
    )}
  }

  trait Js extends ScalaJSModule {
    override def millSourcePath = jvmBase.millSourcePath / os.up / "js"
    override def scalaJSVersion = Deps.scalaJsVersion
    override def scalaVersion = jvmBase.scalaVersion
    override def sources: Sources = T.sources(millSourcePath / os.up / "shared" / "src" / "main" / "scala")
    override def moduleKind: T[ModuleKind] = T{ ModuleKind.CommonJSModule }
    trait Tests extends super.Tests {
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        Deps.js.scalatest
      )}
      override def testFrameworks = Seq("org.scalatest.tools.Framework")
      override def moduleKind: T[ModuleKind] = T{ ModuleKind.CommonJSModule }
    }
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
        Deps.camelJms,
        Deps.activeMqBroker,
        Deps.activeMqSpring
      )}
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.akka,
        blended.jms.utils
      )
      override def osgiHeaders = T{ super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.BrokerActivator")
      )}
      object test extends Tests {
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          Deps.logbackClassic,
          Deps.activeMqKahadbStore
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
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        Deps.logbackClassic
      )}
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
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          Deps.akkaTestkit,
          Deps.akkaSlf4j,
          Deps.mockitoAll,
          Deps.akkaHttpTestkit,
          Deps.akkaStreamTestkit,
          Deps.logbackClassic
        )}
        override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
          blended.testsupport,
          blended.testsupport.pojosr
        )
      }

      object api extends BlendedModule {
        override val description : String = "Package the Akka Http API into a bundle."
        override def ivyDeps = T{ super.ivyDeps() ++ Agg(
          Deps.akkaHttp.exclude("*" -> "*"),
          Deps.akkaHttpCore.exclude("*" -> "*"),
          Deps.akkaParsing.exclude("*" -> "*")
        )}
        override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Import-Package` = Seq(
            "com.sun.*;resolution:=optional",
            "sun.*;resolution:=optional",
            "net.liftweb.*;resolution:=optional",
            "play.*;resolution:=optional",
            "twirl.*;resolution:=optional",
            "org.json4s.*;resolution:=optional",
            "*"
          )
        )}
        override def exportContents: T[Seq[String]] = T{ Seq(
          s"akka.http.*;version=${Deps.akkaHttpVersion};-split-package:=merge-first"
        )}
        override def embeddedJars: T[Seq[PathRef]] = T{
          resolveDeps(ivyDeps)().toSeq
        }
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
            Deps.logbackClassic,
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
            Deps.akkaHttpTestkit,
            Deps.logbackClassic
          )}
          override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(
            blended.testsupport,
            blended.testsupport.pojosr
          )
        }
      }
    }
  }

  object camel extends Module {
    object utils extends BlendedModule {
      override val description : String = "Useful helpers for Camel"
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        Deps.orgOsgi,
        Deps.orgOsgiCompendium,
        Deps.camelJms,
        Deps.slf4j
      )}
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        blended.akka
      )
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
            "blended.launcher.runtime;resolution:=optional"
          )
        )}
        override def moduleDeps = Seq(
          security.crypto
        )
      }

      object impl extends BlendedModule {
        override def description = "A simple OSGi service to provide access to the container's config directory"
        override def ivyDeps = Agg(
          Deps.orgOsgiCompendium,
          Deps.orgOsgi,
          Deps.domino,
          Deps.slf4j,
          Deps.julToSlf4j,
          Deps.springExpression
        )
        override def moduleDeps = Seq(
          blended.security.crypto,
          blended.container.context.api,
          blended.util.logging,
          blended.util,
          blended.updater.config,
          blended.launcher
        )
      }
    }
  }

  object jms extends Module {
    object utils extends BlendedModule {
      override val description = "A bundle to provide a ConnectionFactory wrapper that monitors a single connection and is able to monitor the connection via an active ping."
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
        Deps.camelJms,
        Deps.jms11Spec
      )}

      override def moduleDeps = super.moduleDeps ++ Seq(
        domino,
        mgmt.base,
        container.context.api,
        updater.config,
        util.logging,
        akka
      )
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
          Deps.akkaSlf4j,
          Deps.akkaStream,
          Deps.activeMqBroker,
          Deps.activeMqKahadbStore,
          Deps.akkaTestkit,
          Deps.logbackClassic
        )}
        override def moduleDeps = super.moduleDeps ++ Seq(
          camel.utils,
          testsupport
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
        Deps.logbackClassic,
        Deps.scalacheck
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
    object test extends Tests {
      override def ivyDeps = super.ivyDeps() ++ Agg(
        Deps.scalatest
      )
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport
      )
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

  object mgmt extends Module {
    object base extends BlendedModule {
      override val description = "Shared classes for management and reporting facility."
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
  }
  object persistence extends BlendedModule {
    override val description : String = "Provide a technology agnostic persistence API with pluggable Data Objects defined in other bundles"
    override def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.slf4j,
      Deps.domino
    )
    override def moduleDeps = super.moduleDeps ++ Seq(
      akka
    )
    object test extends Tests {
      override def ivyDeps = super.ivyDeps() ++ Agg(
        Deps.mockitoAll,
        Deps.slf4jLog4j12
      )
      override def moduleDeps = super.moduleDeps ++ Seq(
        testsupport
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
          Deps.jclOverSlf4j,
          Deps.logbackClassic,
          Deps.lambdaTest,
          Deps.scalacheck
        )
        override def moduleDeps = super.moduleDeps ++ Seq(
          updater.config.test
        )
      }
    }
  }

  object security extends BlendedJvmModule {
    override def description = "Configuration bundle for the security framework"
    override def ivyDeps = Agg(
      Deps.prickle
    )
    override def moduleDeps = Seq(
      util.logging,
      domino,
      util,
      security.boot
    )
    override def osgiHeaders = T{ super.osgiHeaders().copy(
      `Bundle-Activator` = Some(s"${blendedModule}.internal.SecurityActivator"),
      `Export-Package` = Seq(
        blendedModule,
        s"${blendedModule}.json"
      )
    )}
    object test extends Tests {
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        Deps.logbackCore,
        Deps.logbackClassic
      )}
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
      override def moduleDeps: Seq[PublishModule] = super.moduleDeps ++ Seq(
        domino,
        util.logging,
        util,
        mgmt.base
      )
      object test extends Tests {
        override def ivyDeps: Target[Loose.Agg[Dep]] = T {
          super.ivyDeps() ++ Agg(
            Deps.logbackClassic,
            Deps.scalacheck
          )
        }
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
    object test extends Tests {
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
        activemq.brokerstarter,
        persistence.h2,
        testsupport.pojosr,
        testsupport
      )
    }
  }

  object testsupport extends BlendedModule {
    override def description = "Some test helper classes"
    override def ivyDeps = Agg(
      Deps.akkaActor,
      Deps.akkaTestkit,
      Deps.akkaCamel,
      Deps.camelCore,
      Deps.camelJms,
      Deps.scalatest,
      Deps.junit,
      Deps.commonsIo
    )

    override def moduleDeps = Seq(
      util,
      util.logging,
      security.boot
    )

    object test extends Tests

    object pojosr extends BlendedModule {
      override def description = "A simple pojo based test container that can be used in unit testing"
      override def ivyDeps = Agg(
        Deps.scalatest,
        Deps.felixConnect,
        Deps.orgOsgi
      )
      override def moduleDeps = Seq(
        util.logging,
        container.context.impl,
        domino
      )
      object test extends Tests
    }

  }

  object updater extends Module {

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
        util.logging,
        security
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
          testsupport
        )
      }

      object js extends Js {
        override def moduleDeps: Seq[JavaModule] = Seq(
          blended.security.js
        )
        object test extends Tests {
          override def ivyDeps: Target[Loose.Agg[Dep]] = T{ super.ivyDeps() ++ Agg(
            Deps.js.prickle
          )}
        }
      }

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

}
