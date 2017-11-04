import sbt._

object Dependencies {

  val akkaVersion = "2.5.6"
  val camelVersion = "2.17.3"
  val parboiledVersion = "1.1.6"
  val prickleVersion = "1.1.14"
  val slf4jVersion = "1.7.25"
  val scalatestVersion = "3.0.4"
  val sprayVersion = "1.3.3"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaCamel = "com.typesafe.akka" %% "akka-camel" % akkaVersion
  val akkaOsgi = "com.typesafe.akka" %% "akka-osgi" % akkaVersion
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

  val camelCore = "org.apache.camel" % "camel-core" % camelVersion
  val cmdOption = "de.tototec" % "de.tototec.cmdoption" % "0.4.2"
  val concurrentLinkedHashMapLru = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"

  val domino = "com.github.domino-osgi" %% "domino" % "1.1.2"

  val julToSlf4j = "org.slf4j" % "jul-to-slf4j" % slf4jVersion
  val junit = "junit" % "junit" % "4.11"

  val logbackCore = "ch.qos.logback" % "logback-core" % "1.2.3"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"

  val microjson = "com.github.benhutchison" %% "microjson" % "1.4"
  val mockitoAll = "org.mockito" % "mockito-all" % "1.9.5"

  val orgOsgi = "org.osgi" % "org.osgi.core" % "5.0.0"

  val prickle = "com.github.benhutchison" %% "prickle" % prickleVersion

  val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion
  val shapeless = "com.chuusai" %% "shapeless" % "1.2.4"
  val slf4j = "org.slf4j" % "slf4j-api" % slf4jVersion

  val sprayClient = "io.spray" %% "spray-client" % sprayVersion
  val sprayCaching = "io.spray" %% "spray-caching" % sprayVersion
  val sprayHttp = "io.spray" %% "spray-http" % sprayVersion
  val sprayHttpx = "io.spray" %% "spray-httpx" % sprayVersion
  val sprayIo = "io.spray" %% "spray-io" % sprayVersion
  val sprayJson = "io.spray" %% "spray-json" % sprayVersion
  val sprayRouting = "io.spray" %% "spray-routing" % sprayVersion
  val sprayServlet = "io.spray" %% "spray-servlet" % sprayVersion
  val sprayTestkit = "io.spray" %% "spray-testkit" % sprayVersion
  val sprayUtil = "io.spray" %% "spray-util" % sprayVersion

  val typesafeConfig = "com.typesafe" % "config" % "1.3.1"
  val typesafeConfigSSL = "com.typesafe" %% "ssl-config-core" % "0.2.2"

}
