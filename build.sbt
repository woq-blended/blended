import sbt._
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

// this is required to use proper values in osgi manifest require capability
val initSystemEarly : Unit = Option(System.getProperty("java.version"))
  .map(v => v.split("[.]", 3).take(2).mkString("."))
  .foreach(v => System.setProperty("java.version", v))

val m2Repo = "file://" + System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository")

inThisBuild(Seq(
  organization := "de.wayofquality.blended",
  homepage := Some(url("https://github.com/woq-blended/blended")),
  version := "2.5.0-SBT-SNAPSHOT",

  licenses += ("Apache 2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),

  developers := List(
    Developer(id = "andreas", name = "Andreas Gies", email = "andreas@wayofquality.de", url = url("https://github.com/atooni")),
    Developer(id = "tobias", name = "Tobias Roeser", email = "tobias.roser@tototec.de", url = url("https://github.com/lefou"))
  ),

  javacOptions in Compile ++= Seq(
    "-source", "1.8",
    "-target", "1.8"
  ),

  scalaVersion := "2.12.6",
  scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint", "-Ywarn-nullary-override"),

  // essential to not try to compile pom.scala files, only required until migration to  sbt is complete
  sourcesInBase := false,
  publishMavenStyle := true,
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    "Maven2 Local" at m2Repo
  )
))

lazy val root = project
  .in(file("."))
  .settings(
    name := "blended",
    unidocProjectFilter.in(ScalaUnidoc, unidoc) := inAnyProject -- inProjects(blendedSecurityJS, blendedUpdaterConfigJS)
  )
  .settings(PublishConfg.noPublish)
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(
    blendedUtilLogging,
    blendedSecurityBoot,
    blendedContainerContextApi,
    blendedDomino,
    blendedUtil,
    blendedTestsupport,
    blendedAkka,
    blendedSecurity.js,
    blendedSecurity.jvm,
    blendedUpdaterConfigJS,
    blendedUpdaterConfigJVM,
    blendedLauncher,
    blendedMgmtBase,
    blendedUpdater
//    blendedContainerContextImpl,
  )

lazy val blendedUtilLogging = project.in(file("blended.util.logging"))
  .settings(BlendedUtilLogging.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedSecurityBoot = project.in(file("blended.security.boot"))
  .settings(BlendedSecurityBoot.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedContainerContextApi = project.in(file("blended.container.context.api"))
  .dependsOn(blendedUtilLogging)
  .settings(BlendedContainerContextApi.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedDomino = project.in(file("blended.domino"))
  .dependsOn(blendedContainerContextApi)
  .settings(BlendedDomino.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedUtil = project.in(file("blended.util"))
  .settings(BlendedUtil.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedTestsupport = project.in(file("blended.testsupport"))
  .dependsOn(blendedUtil, blendedUtilLogging, blendedSecurityBoot)
  .settings(BlendedTestsupport.settings)

lazy val blendedAkka = project.in(file("blended.akka"))
  .dependsOn(blendedUtilLogging, blendedContainerContextApi, blendedDomino)
  .settings(BlendedAkka.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedSecurity = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .in(file("blended.security"))

lazy val blendedSecurityJVM = blendedSecurity.jvm
  .dependsOn(blendedUtilLogging, blendedDomino, blendedUtil, blendedSecurityBoot)
  .settings(BlendedSecurityJVM.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedSecurityJS = blendedSecurity.js
  .settings(
    libraryDependencies ++= BlendedSecurityJS.libDependencies.value
  )

lazy val blendedUpdaterConfig = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .in(file("blended.updater.config"))

lazy val blendedUpdaterConfigJVM = blendedUpdaterConfig.jvm
  .dependsOn(blendedUtilLogging, blendedSecurityJVM, blendedTestsupport % "test")
  .settings(BlendedUpdaterConfigJVM.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedUpdaterConfigJS = blendedUpdaterConfig.js
  .dependsOn(blendedSecurityJS)
  .settings(
    libraryDependencies ++= BlendedSecurityJS.libDependencies.value
  )

lazy val blendedLauncher = project.in(file("blended.launcher"))
  .dependsOn(blendedUtilLogging, blendedUpdaterConfigJVM, blendedAkka, blendedTestsupport % "test")
  .settings(BlendedLauncher.settings)
  .enablePlugins(SbtOsgi, UniversalPlugin, UniversalDeployPlugin, FilterResources)


lazy val blendedMgmtBase = project.in(file("blended.mgmt.base"))
  .dependsOn(blendedDomino, blendedContainerContextApi, blendedUtil, blendedUtilLogging)
  .settings(BlendedMgmtBase.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedUpdater = project.in(file("blended.updater"))
  .dependsOn(
    blendedUpdaterConfigJVM, blendedLauncher, blendedMgmtBase, blendedContainerContextApi, blendedAkka,
    blendedTestsupport % "test"
  )
  .settings(BlendedUpdater.settings)
  .enablePlugins(SbtOsgi)

//lazy val blendedContainerContextImpl = project.in(file("blended.container.context.impl"))
//  .settings(commonSettings)
//  .dependsOn(
//    blendedContainerContextApi
//  )
//
//