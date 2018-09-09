import sbt._
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

// this is required to use proper values in osgi manifest require capability
val initSystemEarly: Unit = Option(System.getProperty("java.version"))
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
    unidocProjectFilter.in(ScalaUnidoc, unidoc) := inAnyProject -- inProjects(blendedSecurityJs, blendedUpdaterConfigJs)
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
    blendedSecurityJs,
    blendedSecurityJvm,
    blendedUpdaterConfigJs,
    blendedUpdaterConfigJvm,
    blendedLauncher,
    blendedMgmtBase,
    blendedUpdater,
    blendedUpdaterTools,
    blendedPersistence,
    blendedUpdaterRemote,
    blendedCamelUtils,
    blendedJmsUtils,
    blendedActivemqBrokerstarter,
    blendedContainerContextImpl,
    blendedJmx,
    blendedJettyBoot,
    blendedJmsSampler
  )

lazy val blendedUtilLogging = BlendedUtilLogging.project

lazy val blendedSecurityBoot = BlendedSecurityBoot.project

lazy val blendedContainerContextApi = BlendedContainerContextApi.project
  .dependsOn(blendedUtilLogging)

lazy val blendedDomino = BlendedDomino.project
  .dependsOn(blendedContainerContextApi)

lazy val blendedUtil = BlendedUtil.project

lazy val blendedTestsupport = BlendedTestsupport.project
  .dependsOn(blendedUtil, blendedUtilLogging, blendedSecurityBoot)

lazy val blendedAkka = BlendedAkka.project
  .dependsOn(blendedUtilLogging, blendedContainerContextApi, blendedDomino)

lazy val blendedSecurityCross = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .in(file("blended.security"))

lazy val blendedSecurityJvm = {
  BlendedSecurityJvm.projectFactory = Some(() => blendedSecurityCross.jvm)
  BlendedSecurityJvm.project
    .dependsOn(blendedUtilLogging, blendedDomino, blendedUtil, blendedSecurityBoot)
}

lazy val blendedSecurityJs = blendedSecurityCross.js
  .settings(
    libraryDependencies ++= BlendedSecurityJs.libDependencies.value
  )

lazy val blendedUpdaterConfigCross = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .in(file("blended.updater.config"))

lazy val blendedUpdaterConfigJvm = {
  BlendedUpdaterConfigJvm.projectFactory = Some(() => blendedUpdaterConfigCross.jvm)
  BlendedUpdaterConfigJvm.project
    .dependsOn(blendedUtilLogging, blendedSecurityJvm, blendedTestsupport % "test")
}

lazy val blendedUpdaterConfigJs = blendedUpdaterConfigCross.js
  .dependsOn(blendedSecurityJs)
  .settings(
    libraryDependencies ++= BlendedSecurityJs.libDependencies.value
  )

lazy val blendedLauncher = BlendedLauncher.project
  .dependsOn(blendedUtilLogging, blendedUpdaterConfigJvm, blendedAkka, blendedTestsupport % "test")

lazy val blendedMgmtBase = BlendedMgmtBase.project
  .dependsOn(blendedDomino, blendedContainerContextApi, blendedUtil, blendedUtilLogging)

lazy val blendedUpdater = BlendedUpdater.project
  .dependsOn(
    blendedUpdaterConfigJvm, blendedLauncher, blendedMgmtBase, blendedContainerContextApi, blendedAkka,
    blendedTestsupport % "test"
  )

lazy val blendedUpdaterTools = BlendedUpdaterTools.project
  .dependsOn(blendedUpdaterConfigJvm)

lazy val blendedPersistence = BlendedPersistence.project
  .dependsOn(blendedAkka, blendedTestsupport % "test")

lazy val blendedUpdaterRemote = BlendedUpdaterRemote.project
  .dependsOn(
    blendedUtilLogging,
    blendedPersistence,
    blendedUpdaterConfigJvm,
    blendedMgmtBase,
    blendedLauncher,
    blendedContainerContextApi,
    blendedAkka,
    blendedTestsupport % "test"
  )

lazy val blendedCamelUtils = BlendedCamelUtils.project
  .dependsOn(blendedAkka)

lazy val blendedJmsUtils = BlendedJmsUtils.project
  .dependsOn(
    blendedDomino,
    blendedMgmtBase,
    blendedContainerContextApi,
    blendedUpdaterConfigJvm,
    blendedUtilLogging,
    blendedAkka,
    blendedCamelUtils % "test",
    blendedTestsupport % "test"
  )

lazy val blendedActivemqBrokerstarter = BlendedActivemqBrokerstarter.project
  .dependsOn(blendedAkka, blendedJmsUtils)

lazy val blendedContainerContextImpl = BlendedContainerContextImpl.project
  .dependsOn(
    blendedContainerContextApi,
    blendedUtilLogging,
    blendedUtil,
    blendedUpdaterConfigJvm,
    blendedLauncher
  )

lazy val blendedJmx = BlendedJmx.project

lazy val blendedJettyBoot = BlendedJettyBoot.project
  .dependsOn(
    blendedDomino,
    blendedUtilLogging
  )

lazy val blendedJmsSampler = BlendedJmsSampler.project
  .dependsOn(blendedDomino, blendedAkka, blendedUtil)
