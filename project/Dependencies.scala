// This file file resides in sbt project dir,
// but is also symlinked to blended.dependencies project
// to be shared with other blended sbt projects
package blended.sbt

import sbt._

trait Dependencies {

  // Versions
  val activeMqVersion = "5.15.6"
  val akkaVersion = "2.5.21"
  val akkaHttpVersion = "10.1.7"
  val dominoVersion = "1.1.3"
  val jettyVersion = "9.4.21.v20190926"
  val jolokiaVersion = "1.5.0"
  val microJsonVersion = "1.4"
  val parboiledVersion = "1.1.6"
  val prickleVersion = "1.1.14"
  val scalaVersion = "2.12.8"
  val scalatestVersion = "3.0.5"
  val scalaCheckVersion = "1.14.0"
  val slf4jVersion = "1.7.25"
  val sprayVersion = "1.3.4"
  val springVersion = "4.3.12.RELEASE_1"

  protected def akka(m : String) : ModuleID = "com.typesafe.akka" %% s"akka-$m" % akkaVersion
  protected def akkaHttpModule(m : String) : ModuleID = "com.typesafe.akka" %% s"akka-$m" % akkaHttpVersion

  val activeMqBroker = "org.apache.activemq" % "activemq-broker" % activeMqVersion
  val activeMqClient = "org.apache.activemq" % "activemq-client" % activeMqVersion
  val activeMqKahadbStore = "org.apache.activemq" % "activemq-kahadb-store" % activeMqVersion
  val activeMqSpring = "org.apache.activemq" % "activemq-spring" % activeMqVersion

  val akkaActor = akka("actor")
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

  val cmdOption = "de.tototec" % "de.tototec.cmdoption" % "0.6.0"
  val commonsBeanUtils = "commons-beanutils" % "commons-beanutils" % "1.9.3"
  val commonsCodec = "commons-codec" % "commons-codec" % "1.11"
  val commonsDaemon = "commons-daemon" % "commons-daemon" % "1.0.15"
  val commonsIo = "commons-io" % "commons-io" % "2.6"
  val commonsLang2 = "commons-lang" % "commons-lang" % "2.6"
  val commonsLogging = "commons-logging" % "commons-logging" % "1.2"
  val concurrentLinkedHashMapLru = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"

  val dockerJava = "com.github.docker-java" % "docker-java" % "3.0.13"
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

  protected def jettyOsgi(n : String) : ModuleID = "org.eclipse.jetty.osgi" % s"jetty-$n" % jettyVersion

  val jcip = "net.jcip" % "jcip-annotations" % "1.0"
  val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % slf4jVersion
  val jettyOsgiBoot = jettyOsgi("osgi-boot")
  val jjwt = "io.jsonwebtoken" % "jjwt" % "0.7.0"
  val jms11Spec = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"
  val jolokiaJvm = "org.jolokia" % "jolokia-jvm" % jolokiaVersion
  val jolokiaJvmAgent = jolokiaJvm.classifier("agent")
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
  protected def spring(n : String): ModuleID = "org.apache.servicemix.bundles" % s"org.apache.servicemix.bundles.spring-$n" % springVersion

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

object Dependencies extends Dependencies
