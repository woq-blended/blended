import sbt._
import sbt.Keys._

object Dependencies {

  val activeMqVersion = "5.15.3"
  val akkaVersion = "2.5.16"
  val camelVersion = "2.17.3"
  val jettyVersion = "9.4.8.v20171121"
  val parboiledVersion = "1.1.6"
  val prickleVersion = "1.1.14"
  val slf4jVersion = "1.7.25"
  val scalatestVersion = "3.0.5"

  val activeMqBroker = "org.apache.activemq" % "activemq-broker" % activeMqVersion
  val activeMqKahadbStore = "org.apache.activemq" % "activemq-kahadb-store" % activeMqVersion
  val activeMqSpring = "org.apache.activemq" % "activemq-spring" % activeMqVersion
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaCamel = "com.typesafe.akka" %% "akka-camel" % akkaVersion
  val akkaOsgi = "com.typesafe.akka" %% "akka-osgi" % akkaVersion
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

  val camelCore = "org.apache.camel" % "camel-core" % camelVersion
  val camelJms = "org.apache.camel" % "camel-jms" % camelVersion
  val cmdOption = "de.tototec" % "de.tototec.cmdoption" % "0.6.0"
  val commonsDaemon = "commons-daemon" % "commons-daemon" % "1.0.15"
  val concurrentLinkedHashMapLru = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"

  val domino = "com.github.domino-osgi" %% "domino" % "1.1.2"

  val felixConnect = "org.apache.felix" % "org.apache.felix.connect" % "0.1.0"
  val felixGogoCommand = "org.apache.felix" % "org.apache.felix.gogo.command" % "0.14.0"
  val felixGogoRuntime = "org.apache.felix" % "org.apache.felix.gogo.runtime" % "0.16.2"
  val felixGogoShell = "org.apache.felix" % "org.apache.felix.gogo.shell" % "0.10.0"
  val felixFileinstall = "org.apache.felix" % "org.apache.felix.fileinstall" % "3.4.2"
  val felixFramework = "org.apache.felix" % "org.apache.felix.framework" % "5.6.10"

  val geronimoJms11Spec = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"

  private def jettyOsgi(n: String) = "org.eclipse.jetty.osgi" % s"jetty-$n" % jettyVersion
  val jettyOsgiBoot = jettyOsgi("osgi-boot")

  val jms11Spec = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"
  val julToSlf4j = "org.slf4j" % "jul-to-slf4j" % slf4jVersion
  val junit = "junit" % "junit" % "4.11"

  val log4s = "org.log4s" %% "log4s" % "1.6.1"
  val logbackCore = "ch.qos.logback" % "logback-core" % "1.2.3"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"

  val microjson = "com.github.benhutchison" %% "microjson" % "1.4"
  val mimepull = "org.jvnet.mimepull" % "mimepull" % "1.9.5"
  val mockitoAll = "org.mockito" % "mockito-all" % "1.9.5"

  val orgOsgi = "org.osgi" % "org.osgi.core" % "6.0.0"
  val orgOsgiCompendium = "org.osgi" % "org.osgi.compendium" % "5.0.0"

  val parboiledCore = "org.parboiled" % "parboiled-core" % parboiledVersion
  val parboiledScala = "org.parboiled" %% "parboiled-scala" % parboiledVersion
  val prickle = "com.github.benhutchison" %% "prickle" % prickleVersion

  val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion
  val shapeless = "com.chuusai" %% "shapeless" % "1.2.4"
  val slf4j = "org.slf4j" % "slf4j-api" % slf4jVersion
  val slf4jLog4j12 = "org.slf4j" % "slf4j-log4j12" % slf4jVersion

  val scalaLibrary = Def.setting("org.scala-lang" % "scala-library" % scalaVersion.value)
  val scalaParser = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1"
  val scalaReflect = Def.setting("org.scala-lang" % "scala-reflect" % scalaVersion.value)
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.1.0"

  val typesafeConfig = "com.typesafe" % "config" % "1.3.1"
  val typesafeConfigSSL = "com.typesafe" %% "ssl-config-core" % "0.2.4"

}

