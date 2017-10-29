import sbt._

object Dependencies {

  val akkaVersion = "2.5.6"
  val slf4jVersion = "1.7.25"
  val scalatestVersion = "3.0.4"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion

  val junit = "junit" % "junit" % "4.11"

  val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion
  val slf4j = "org.slf4j" % "slf4j-api" % slf4jVersion
}
