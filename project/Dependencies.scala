import sbt._

object Dependencies {

  val akkaVersion = "2.5.6"
  val camelVersion = "2.17.3"
  val prickleVersion = "1.1.14"
  val slf4jVersion = "1.7.25"
  val scalatestVersion = "3.0.4"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaCamel = "com.typesafe.akka" %% "akka-camel" % akkaVersion
  val akkaOsgi = "com.typesafe.akka" %% "akka-osgi" % akkaVersion
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

  val camelCore = "org.apache.camel" % "camel-core" % camelVersion

  val junit = "junit" % "junit" % "4.11"

  val logbackCore = "ch.qos.logback" % "logback-core" % "1.2.3"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"

  val microjson = "com.github.benhutchison" %% "microjson" % "1.4"

  val prickle = "com.github.benhutchison" %% "prickle" % prickleVersion

  val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion
  val slf4j = "org.slf4j" % "slf4j-api" % slf4jVersion

  val typesafeConfig = "com.typesafe" % "config" % "1.3.1"
  val typesafeConfigSSL = "com.typesafe" %% "ssl-config-core" % "0.2.2"

}
