import sbt.Keys._
import sbt._

val m2Repo = "file://" + System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository")


lazy val root = project
  .in(file("."))
  .settings(inThisBuild(Seq(
    organization := BlendedVersions.blendedGroupId,
    homepage := Some(url("https://github.com/woq-blended/blended")),
    version := BlendedVersions.blended,
    licenses += ("Apache 2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    developers := List(
      Developer(id = "andreas", name = "Andreas Gies", email = "andreas@wayofquality.de", url = url("https://github.com/woq-blended/blended")),
      Developer(id = "tobias", name = "Tobias Roeser", email = "tobias.roser@tototec.de", url = url("https://github.com/woq-blended/blended"))
    ),

    crossScalaVersions := Seq(BlendedVersions.scala), //Seq("2.11.11", "2.12.4"),
    scalaVersion := BlendedVersions.scala,
    scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint", "-Ywarn-nullary-override"),
    sourcesInBase := false,
    publishMavenStyle := true

  )))
  .settings(
    name := "blended",
    publish := {},
    publishLocal := {},
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(blendedUpdaterConfigJs)
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

lazy val blendedTestsupport = project.in(file("blended.testsupport"))
  .dependsOn(blendedUtil)

lazy val blendedUpdaterConfig = crossProject.in(file("blended.updater.config"))
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.prickle.organization %%% Dependencies.prickle.name % Dependencies.prickleVersion,
      Dependencies.scalatest.organization %%% Dependencies.scalatest.name % Dependencies.scalatestVersion % "test"
    )
  )
  .jvmSettings(BuildHelper.bundleSettings(
    exportPkgs = Seq("", "json", "util", "/blended.launcher.config"),
    importPkgs = Seq.empty
  ):_*)
  .jvmSettings(
    unmanagedResourceDirectories in Compile += baseDirectory.value / "src" / "main" / "binaryResources",
    unmanagedResourceDirectories in Test += baseDirectory.value / "src" / "test" / "binaryResources",
    javaOptions in Test += ("-DprojectTestOutput=" + target.value / s"scala-${scalaBinaryVersion.value}" / "test-classes") ,
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
  .dependsOn(blendedUpdaterConfigJvm)

lazy val blendedContainerContext = project.in(file("blended.container.context"))
  .dependsOn(blendedUpdaterConfigJvm, blendedLauncher)

lazy val blendedDomino = project.in(file("blended.domino"))
  .dependsOn(blendedContainerContext)

lazy val blendedMgmtBase = project.in(file("blended.mgmt.base"))
  .dependsOn(blendedUtil, blendedDomino, blendedUpdaterConfigJvm)

lazy val blendedAkka = project.in(file("blended.akka"))
  .dependsOn(blendedContainerContext, blendedDomino)

lazy val blendedSprayApi = project.in(file("blended.spray.api"))
