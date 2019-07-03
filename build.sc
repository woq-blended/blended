import scala.util.Try

import ammonite.runtime.tools.IvyThing
import mill.{PathRef, _}
import mill.define.Target
import mill.scalalib._
import mill.scalalib.publish._
import os.Path

// This import the mill-osgi plugin
import $ivy.`de.tototec::de.tobiasroeser.mill.osgi:0.0.6`
import de.tobiasroeser.mill.osgi._

trait BlendedModule extends SbtModule with PublishModule with OsgiBundleModule {
  def scalaVersion = "2.12.8"

  def publishVersion = "3.1-SNAPSHOT"

  def blendedModule: String

  /** The module description. */
  def description: String = s"Blended module ${blendedModule}"

  /** Reference to the base project. */
  //  def blendedBase: Module

  override def millSourcePath: os.Path = blended.millOuterCtx.millSourcePath / blendedModule

  override def resources = T.sources {
    super.resources() ++ Seq(
      PathRef(millSourcePath / 'src / 'main / 'binaryResources)
    )
  }

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

  trait Tests extends super.Tests {
    override def ivyDeps = Deps(Dependencies.scalatest)

    def testFrameworks = Seq("org.scalatest.tools.Framework")

    override def resources = T.sources {
      super.resources() ++ Seq(
        PathRef(millSourcePath / 'src / 'test / 'binaryResources)
      )
    }
    // TODO: set projectTestOutput property to resources directory
  }

  /** Show all compiled classes. */
  def classes: T[Seq[Path]] = T {
    Try(os.walk(compile().classes.path)).getOrElse(Seq())
  }

  import ammonite.runtime.tools.IvyConstructor._

  implicit def coursierToMillDep(dep: coursier.Dependency): Dep = ivy"${dep.module.organization}:${
    val suffix = s"_${IvyThing.scalaBinaryVersion}"
    if (dep.module.name.endsWith(suffix)) {
      ":" + dep.module.name.substring(0, dep.module.name.length - suffix.length())
    } else {
      dep.module.name
    }
  }:${dep.version}"

  object Deps {
    def apply(deps: coursier.Dependency*): Agg[Dep] = Agg[Dep](deps.map(d => coursierToMillDep(d)): _*)
  }

  object Dependencies {

    //    class Group(group: String) {
    //      def %(artifact: String): GroupArtifact = GroupArtifact(group, artifact, isScala = false)
    //
    //      def %%(artifact: String): GroupArtifact = GroupArtifact(group, artifact, isScala = true)
    //    }
    //
    //    case class GroupArtifact(group: String, artifact: String, isScala: Boolean) {
    //      def %(version: String): Dep = ivy"${group}:${if (isScala) ":" else ""}${artifact}:${version}"
    //    }
    //
    //    implicit def implGroup(group: String): Group = new Group(group)

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
    val scalaVersion = "2.12.8"
    val scalatestVersion = "3.0.5"
    val scalaCheckVersion = "1.14.0"
    val slf4jVersion = "1.7.25"
    val sprayVersion = "1.3.4"
    val springVersion = "4.3.12.RELEASE_1"

    protected def akka(m: String) = "com.typesafe.akka" %% s"akka-${m}" % akkaVersion

    protected def akkaHttpModule(m: String) = "com.typesafe.akka" %% s"akka-${m}" % akkaHttpVersion

    val activeMqBroker = "org.apache.activemq" % "activemq-broker" % activeMqVersion
    val activeMqClient = "org.apache.activemq" % "activemq-client" % activeMqVersion
    val activeMqKahadbStore = "org.apache.activemq" % "activemq-kahadb-store" % activeMqVersion
    val activeMqSpring = "org.apache.activemq" % "activemq-spring" % activeMqVersion
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

    val asciiRender = "com.indvd00m.ascii.render" % "ascii-render" % "1.2.3"

    val bouncyCastleBcprov = "org.bouncycastle" % "bcprov-jdk15on" % "1.60"
    val bouncyCastlePkix = "org.bouncycastle" % "bcpkix-jdk15on" % "1.60"

    val camelCore = "org.apache.camel" % "camel-core" % camelVersion
    val camelJms = "org.apache.camel" % "camel-jms" % camelVersion

    val cmdOption = "de.tototec" % "de.tototec.cmdoption" % "0.6.0"
    val commonsBeanUtils = "commons-beanutils" % "commons-beanutils" % "1.9.3"
    val commonsCodec = "commons-codec" % "commons-codec" % "1.11"
    val commonsDaemon = "commons-daemon" % "commons-daemon" % "1.0.15"
    val commonsIo = "commons-io" % "commons-io" % "2.6"
    val commonsLang2 = "commons-lang" % "commons-lang" % "2.6"
    val concurrentLinkedHashMapLru = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"

    val domino = "com.github.domino-osgi" %% "domino" % dominoVersion

    val felixConnect = "org.apache.felix" % "org.apache.felix.connect" % "0.1.0"
    val felixGogoCommand = "org.apache.felix" % "org.apache.felix.gogo.command" % "1.1.0"
    val felixGogoJline = "org.apache.felix" % "org.apache.felix.gogo.jline" % "1.1.4"
    val felixGogoShell = "org.apache.felix" % "org.apache.felix.gogo.shell" % "1.1.2"
    val felixGogoRuntime = "org.apache.felix" % "org.apache.felix.gogo.runtime" % "1.1.2"
    val felixFileinstall = "org.apache.felix" % "org.apache.felix.fileinstall" % "3.4.2"
    val felixFramework = "org.apache.felix" % "org.apache.felix.framework" % "6.0.2"

    val geronimoJms11Spec = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"

    val h2 = "com.h2database" % "h2" % "1.4.197"
    val hikaricp = "com.zaxxer" % "HikariCP" % "3.1.0"

    protected def jettyOsgi(n: String) = "org.eclipse.jetty.osgi" % s"jetty-$n" % jettyVersion

    val jcip = "net.jcip" % "jcip-annotations" % "1.0"
    val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % slf4jVersion
    val jettyOsgiBoot = jettyOsgi("osgi-boot")
    val jjwt = "io.jsonwebtoken" % "jjwt" % "0.7.0"
    val jms11Spec = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"
    val jolokiaJvm = "org.jolokia" % "jolokia-jvm" % jolokiaVersion
    //    val jolokiaJvmAgent = jolokiaJvm.classifier("agent")
    val jscep = "com.google.code.jscep" % "jscep" % "2.5.0"
    val jsonLenses = "net.virtual-void" %% "json-lenses" % "0.6.2"
    val julToSlf4j = "org.slf4j" % "jul-to-slf4j" % slf4jVersion
    val junit = "junit" % "junit" % "4.12"

    val lambdaTest = "de.tototec" % "de.tobiasroeser.lambdatest" % "0.6.2"
    val levelDbJava = "org.iq80.leveldb" % "leveldb" % "0.9"
    val levelDbJni = "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"
    val liquibase = "org.liquibase" % "liquibase-core" % "3.6.1"
    /** Only for use in test that also runs in JS */
    val log4s = "org.log4s" %% "log4s" % "1.6.1"
    val logbackCore = "ch.qos.logback" % "logback-core" % "1.2.3"
    val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"

    val microjson = "com.github.benhutchison" %% "microjson" % microJsonVersion
    val mimepull = "org.jvnet.mimepull" % "mimepull" % "1.9.5"
    val mockitoAll = "org.mockito" % "mockito-all" % "1.9.5"

    val orgOsgi = "org.osgi" % "org.osgi.core" % "6.0.0"
    val orgOsgiCompendium = "org.osgi" % "org.osgi.compendium" % "5.0.0"

    val parboiledCore = "org.parboiled" % "parboiled-core" % parboiledVersion
    val parboiledScala = "org.parboiled" %% "parboiled-scala" % parboiledVersion
    val prickle = "com.github.benhutchison" %% "prickle" % prickleVersion

    // SCALA
    val scalaLibrary = "org.scala-lang" % "scala-library" % scalaVersion
    val scalaReflect = "org.scala-lang" % "scala-reflect" % scalaVersion
    val scalaParser = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1"
    val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.1.0"

    val scalacheck = "org.scalacheck" %% "scalacheck" % "1.14.0"
    val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion
    val shapeless = "com.chuusai" %% "shapeless" % "1.2.4"
    val slf4j = "org.slf4j" % "slf4j-api" % slf4jVersion
    val slf4jLog4j12 = "org.slf4j" % "slf4j-log4j12" % slf4jVersion
    val snakeyaml = "org.yaml" % "snakeyaml" % "1.18"
    val sprayJson = "io.spray" %% s"spray-json" % sprayVersion

    //  protected def spring(n: String) = "org.springframework" % s"spring-${n}" % springVersion
    protected def spring(n: String) = "org.apache.servicemix.bundles" % s"org.apache.servicemix.bundles.spring-${n}" % springVersion

    val springBeans = spring("beans")
    val springAop = spring("aop")
    val springContext = spring("context")
    val springContextSupport = spring("context-support")
    val springExpression = spring("expression")
    val springCore = spring("core")
    val springJdbc = spring("jdbc")
    val springJms = spring("jms")
    val springTx = spring("tx")

    val sttp = "com.softwaremill.sttp" %% "core" % "1.3.0"
    val sttpAkka = "com.softwaremill.sttp" %% "akka-http-backend" % "1.3.0"

    val travesty = "net.mikolak" %% "travesty" % s"0.9.1_2.5.17"

    val typesafeConfig = "com.typesafe" % "config" % "1.3.3"
    val typesafeSslConfigCore = "com.typesafe" %% "ssl-config-core" % "0.3.6"

  }

}

trait BlendedJvmModule extends BlendedModule {
  override def millSourcePath = super.millSourcePath / "jvm"
  override def sources = T.sources {
    super.sources() ++ Seq(PathRef(millSourcePath / os.up / 'shared / 'src / 'main / 'scala))
  }
  override def resources = T.sources {
    super.resources() ++ Seq(
      PathRef(millSourcePath / os.up / 'shared / 'src / 'main / 'resources),
      PathRef(millSourcePath / os.up / 'shared / 'src / 'main / 'binaryResources)
    )
  }

  trait Tests extends super.Tests {
    override def sources = T.sources {
      super.sources() ++ Seq(PathRef(millSourcePath / os.up / 'shared / 'src / 'test / 'scala))
    }
    override def resources = T.sources {
      super.resources() ++ Seq(
        PathRef(millSourcePath / os.up / 'shared / 'src / 'test / 'resources),
        PathRef(millSourcePath / os.up / 'shared / 'src / 'test / 'binaryResources)
      )
    }
  }
}

object blended extends Module {

  object container extends Module {
    object context extends Module {

      object api extends BlendedModule {
        def blendedModule = "blended.container.context.api"
        override def description = "The API for the Container Context and Identifier Service"
        override def ivyDeps = Deps(
          Dependencies.typesafeConfig
        )
        override def osgiHeaders = T {
          super.osgiHeaders().copy(
            `Import-Package` = Seq(
              "blended.launcher.runtime;resolution:=optional"
            )
          )
        }
        override def moduleDeps = Seq(
          security.crypto
        )
      }

      object impl extends BlendedModule {
        def blendedModule = "blended.container.context.impl"
        override def description = "A simple OSGi service to provide access to the container's config directory"
        override def ivyDeps = Deps(
          Dependencies.orgOsgiCompendium,
          Dependencies.orgOsgi,
          Dependencies.domino,
          Dependencies.slf4j,
          Dependencies.julToSlf4j,
          Dependencies.springExpression
        )
        override def moduleDeps = Seq(
          blended.security.crypto,
          blended.container.context.api,
          blended.util.logging,
          blended.util,
          blended.updater.config.jvm,
          blended.launcher
        )
      }
    }
  }

  object launcher extends BlendedModule {
    override def blendedModule = "blended.launcher"
    override def description = "Provide an OSGi Launcher"
    override def ivyDeps = Deps(
      Dependencies.cmdOption,
      Dependencies.orgOsgi,
      Dependencies.typesafeConfig,
      Dependencies.logbackCore,
      Dependencies.logbackClassic,
      Dependencies.commonsDaemon
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
      blended.updater.config.jvm,
      blended.security.crypto
    )

    object test extends Tests {
      override def ivyDeps = super.ivyDeps() ++ Deps(
        Dependencies.scalatest
      )
      override def moduleDeps = super.moduleDeps ++ Seq(
        blended.testsupport
      )
    }
  }

  object domino extends BlendedModule {
    override def blendedModule = "blended.domino"
    override def description = "Blended Domino extension for new Capsule scopes"
    override def ivyDeps = Deps(
      Dependencies.typesafeConfig,
      Dependencies.domino
    )
    override def moduleDeps = Seq(
      blended.util.logging,
      blended.container.context.api
    )
    object test extends Tests
  }

  object security extends Module {

    object jvm extends BlendedJvmModule {
      override def blendedModule = "blended.security"
      override def description = "Configuration bundle for the security framework"
      override def ivyDeps = Deps(
        Dependencies.prickle
      )
      override def osgiHeaders = super.osgiHeaders().copy(
        `Bundle-Activator` = Some(s"${blendedModule}.internal.SecurityActivator"),
        `Export-Package` = Seq(
          blendedModule,
          s"${blendedModule}.json"
        )
      )
      override def moduleDeps = Seq(
        util.logging,
        domino,
        util,
        security.boot
      )

      object test extends Tests {
        override def ivyDeps = super.ivyDeps() ++ Deps(
          Dependencies.logbackCore,
          Dependencies.logbackClassic
        )
      }
    }

    object boot extends BlendedModule {
      override def blendedModule = "blended.security.boot"
      override def description: String = "A delegating login module for the blended container"
      override def compileIvyDeps: Target[Agg[Dep]] = Deps(
        Dependencies.orgOsgi
      )
      override def osgiHeaders = T {
        super.osgiHeaders().copy(
          `Fragment-Host` = Some("system.bundle;extension:=framework"),
          `Import-Package` = Seq("")
        )
      }
    }

    object crypto extends BlendedModule {
      def blendedModule = "blended.security.crypto"
      override def description = "Provides classes and mainline for encrypting / decrypting arbitrary Strings"
      override def ivyDeps = Deps(
        Dependencies.cmdOption
      )
      override def osgiHeaders = T {
        super.osgiHeaders().copy(
          `Import-Package` = Seq(
            "de.tototec.cmdoption;resolution:=optional"
          ),
          `Export-Package` = Seq(
            blendedModule
          )
        )
      }
      object test extends Tests {
        override def ivyDeps = super.ivyDeps() ++ Deps(
          Dependencies.scalacheck,
          Dependencies.logbackCore,
          Dependencies.logbackClassic
        )
        override def moduleDeps = super.moduleDeps ++ Seq(
          testsupport
        )
      }
    }
  }

  object testsupport extends BlendedModule {
    override def blendedModule = "blended.testsupport"
    override def description = "Some test helper classes"
    override def ivyDeps = Deps(
      Dependencies.akkaActor,
      Dependencies.akkaTestkit,
      Dependencies.akkaCamel,
      Dependencies.camelCore,
      Dependencies.camelJms,
      Dependencies.scalatest,
      Dependencies.junit,
      Dependencies.commonsIo
    )
    override def moduleDeps = Seq(
      util,
      util.logging,
      security.boot
    )

    object test extends Tests

    object pojosr extends BlendedModule {
      override def blendedModule = "blended.testsupport.pojosr"
      override def description = "A simple pojo based test container that can be used in unit testing"
      override def ivyDeps = Deps(
        Dependencies.scalatest,
        Dependencies.felixConnect,
        Dependencies.orgOsgi
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
    object config extends Module {
      object jvm extends BlendedJvmModule {
        override def blendedModule = "blended.updater.config"
        override def description = "Configurations for Updater and Launcher"
        override def ivyDeps = Deps(
          Dependencies.prickle,
          Dependencies.typesafeConfig
        )
        override def osgiHeaders = super.osgiHeaders().copy(
          `Export-Package` = Seq(
            blendedModule,
            s"${blendedModule}.json",
            s"${blendedModule}.util",
            "blended.launcher.config"
          )
        )
        override def moduleDeps = Seq(
          util.logging,
          security.jvm
        )

        object test extends Tests {
          override def ivyDeps = super.ivyDeps() ++ Deps(
            Dependencies.scalatest,
            Dependencies.logbackClassic,
            Dependencies.logbackCore,
            Dependencies.scalacheck,
            Dependencies.log4s
          )
          override def moduleDeps = super.moduleDeps ++ Seq(
            testsupport
          )
        }
      }
    }
  }

  object util extends BlendedModule {
    override def blendedModule = "blended.util"

    override def description: String = "Utility classes to use in other bundles"

    override def compileIvyDeps: Target[Agg[Dep]] = Deps(
      Dependencies.akkaActor,
      Dependencies.akkaSlf4j,
      Dependencies.slf4j
    )

    override def osgiHeaders = T {
      super.osgiHeaders().copy(
        `Export-Package` = Seq(
          blendedModule,
          s"${blendedModule}.config"
        )
      )
    }

    object test extends Tests {
      override def ivyDeps = super.ivyDeps() ++ Deps(
        Dependencies.akkaTestkit,
        Dependencies.junit,
        Dependencies.logbackClassic,
        Dependencies.logbackCore
      )
    }

    object logging extends BlendedModule {
      override def blendedModule: String = "blended.util.logging"

      override def description: String = "Logging utility classes to use in other bundles"

      override def compileIvyDeps: Target[Agg[Dep]] = Deps(
        Dependencies.slf4j
      )

      object test extends Tests

    }

  }

}
