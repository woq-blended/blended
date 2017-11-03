import sbt._
import sbt.Keys._

name := "blended.testsupport"

description := "Some test helper classes."

libraryDependencies ++= Seq(
  Dependencies.akkaActor,
  Dependencies.akkaCamel,
  Dependencies.akkaTestkit,
  Dependencies.camelCore,
  Dependencies.scalatest % "test",
  Dependencies.junit % "test",
  Dependencies.logbackCore % "test",
  Dependencies.logbackClassic % "test"
)