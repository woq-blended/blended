
import sbt.Keys._
import sbt._

// this is required to use proper values in osgi manifest require capability
val initSystemEarly = Option(System.getProperty("java.version"))
  .map(v => v.split("[.]", 3).take(2).mkString("."))
  .foreach(v => System.setProperty("java.version", v))

val m2Repo = "file://" + System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository")

lazy val commonSettings = Seq(
  organization := BlendedVersions.blendedGroupId,
  homepage := Some(url("https://github.com/woq-blended/blended")),
  version := BlendedVersions.blended,
  licenses += ("Apache 2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  developers := List(
    Developer(id = "andreas", name = "Andreas Gies", email = "andreas@wayofquality.de", url = url("https://github.com/atooni")),
    Developer(id = "tobias", name = "Tobias Roeser", email = "tobias.roser@tototec.de", url = url("https://github.com/lefou"))
  ),

  crossScalaVersions := Seq(BlendedVersions.scala), //Seq(BlendedVersions.scala, "2.12.4"),
  scalaVersion := BlendedVersions.scala,
  scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint", "-Ywarn-nullary-override"),
  // essential to not try to compile pom.scala files
  sourcesInBase := false,
  publishMavenStyle := true
)

lazy val root = project
  .in(file("."))
  .settings(
    commonSettings,
    name := "blended",
    publish := {},
    publishLocal := {},
    unidocProjectFilter.in(ScalaUnidoc, unidoc) := inAnyProject -- inProjects(blendedUpdaterConfigJs)
  )
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(
    blendedUtil,
    blendedTestsupport,
    blendedUpdaterConfigJs,
    blendedUpdaterConfigJvm,
    blendedLauncher,
    blendedContainerContext,
    blendedDomino,
    blendedMgmtBase,
    blendedAkka,
    blendedSprayApi
  )

lazy val blendedUtil = project.in(file("blended.util"))
  .settings(commonSettings)

lazy val blendedTestsupport = project.in(file("blended.testsupport"))
  .settings(commonSettings)
  .dependsOn(blendedUtil)

lazy val blendedUpdaterConfig = crossProject.in(file("blended.updater.config"))
  //  .enablePlugins(BlendedPlugin)
  .settings(commonSettings,
  libraryDependencies ++= Seq(
    Dependencies.prickle.organization %%% Dependencies.prickle.name % Dependencies.prickleVersion,
    Dependencies.scalatest.organization %%% Dependencies.scalatest.name % Dependencies.scalatestVersion % "test"
  )
)
  .jvmSettings(BuildHelper.bundleSettings(
    exportPkgs = Seq("", "json", "util", "/blended.launcher.config"),
    importPkgs = Seq.empty
  ): _*)
  .jvmSettings(
    unmanagedResourceDirectories in Compile += baseDirectory.value / "src" / "main" / "binaryResources",
    unmanagedResourceDirectories in Test += baseDirectory.value / "src" / "test" / "binaryResources",
    javaOptions in Test += ("-DprojectTestOutput=" + target.value / s"scala-${scalaBinaryVersion.value}" / "test-classes"),
    fork in Test := true,
    libraryDependencies ++= Seq(
      Dependencies.typesafeConfig,
      Dependencies.slf4j,
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )
  )

lazy val blendedUpdaterConfigJvm = blendedUpdaterConfig.jvm
  .dependsOn(blendedTestsupport % "test")
  .enablePlugins(SbtOsgi)

lazy val blendedUpdaterConfigJs = blendedUpdaterConfig.js

lazy val blendedLauncher = project.in(file("blended.launcher"))
  .settings(commonSettings)
  .dependsOn(blendedUpdaterConfigJvm)

lazy val blendedContainerContext = project.in(file("blended.container.context"))
  .settings(commonSettings)
  .dependsOn(
    blendedUpdaterConfigJvm,
    blendedLauncher
  )

lazy val blendedDomino = project.in(file("blended.domino"))
  .settings(commonSettings)
  .dependsOn(blendedContainerContext)

lazy val blendedMgmtBase = project.in(file("blended.mgmt.base"))
  .settings(commonSettings)
  .dependsOn(
    blendedUtil,
    blendedDomino,
    blendedUpdaterConfigJvm
  )

lazy val blendedAkka = project.in(file("blended.akka"))
  .settings(commonSettings)
  .dependsOn(
    blendedContainerContext,
    blendedDomino
  )

lazy val blendedSprayApi = project.in(file("blended.spray.api"))
  .settings(commonSettings)
