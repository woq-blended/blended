import sbt._
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

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
  publishMavenStyle := true
))

// General settings for subprojects to be published
lazy val doPublish = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  credentials += Credentials(Path.userHome / ".sbt" / "sonatype.credentials"),
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if(isSnapshot.value) {
      Some("snapshots" at nexus + "content/repositories/snapshots")
    } else {
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  }
)

// General settings for subprojects not to be published
lazy val noPublish = Seq(
  publishArtifact := false,
  publishLocal := {}
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "blended",
    unidocProjectFilter.in(ScalaUnidoc, unidoc) := inAnyProject -- inProjects(blendedSecurityJS)
  )
  .settings(noPublish)
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
    blendedUpdaterConfigJVM
//    blendedLauncher,
//    blendedContainerContextImpl,
//    blendedMgmtBase,
  )

lazy val blendedUtilLogging = project.in(file("blended.util.logging"))
  .settings(doPublish)
  .settings(BlendedUtilLogging.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedSecurityBoot = project.in(file("blended.security.boot"))
  .settings(doPublish)
  .settings(BlendedSecurityBoot.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedContainerContextApi = project.in(file("blended.container.context.api"))
  .settings(doPublish)
  .settings(BlendedContainerContextApi.settings)
  .dependsOn(blendedUtilLogging)
  .enablePlugins(SbtOsgi)

lazy val blendedDomino = project.in(file("blended.domino"))
  .settings(doPublish)
  .settings(BlendedDomino.settings)
  .dependsOn(blendedContainerContextApi)
  .enablePlugins(SbtOsgi)

lazy val blendedUtil = project.in(file("blended.util"))
  .settings(doPublish)
  .settings(BlendedUtil.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedTestsupport = project.in(file("blended.testsupport"))
  .settings(doPublish)
  .settings(BlendedTestsupport.settings)
  .dependsOn(blendedUtil, blendedUtilLogging, blendedSecurityBoot)

lazy val blendedAkka = project.in(file("blended.akka"))
  .settings(doPublish)
  .settings(BlendedAkka.settings)
  .dependsOn(blendedUtilLogging, blendedContainerContextApi, blendedDomino)
  .enablePlugins(SbtOsgi)

lazy val blendedSecurity = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .settings(doPublish)
  .in(file("blended.security"))

lazy val blendedSecurityJVM = blendedSecurity.jvm
  .settings(BlendedSecurityJVM.settings)
  .dependsOn(blendedUtilLogging, blendedDomino, blendedUtil, blendedSecurityBoot)
  .enablePlugins(SbtOsgi)

lazy val blendedSecurityJS = blendedSecurity.js
  .settings(
    libraryDependencies ++= BlendedSecurityJS.libDependencies.value
  )

lazy val blendedUpdaterConfig = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .settings(doPublish)
  .in(file("blended.updater.config"))

lazy val blendedUpdaterConfigJVM = blendedUpdaterConfig.jvm
  .settings(BlendedUpdaterConfigJVM.settings)
  .dependsOn(blendedUtilLogging, blendedSecurityJVM, blendedTestsupport % "test")
  .enablePlugins(SbtOsgi)

lazy val blendedUpdaterConfigJS = blendedUpdaterConfig.js
  .settings(
    libraryDependencies ++= BlendedSecurityJS.libDependencies.value
  )
  .dependsOn(blendedSecurityJS)

//
//lazy val blendedLauncher = project.in(file("blended.launcher"))
//  .settings(commonSettings)
//  .dependsOn(blendedUpdaterConfigJvm)
//
//lazy val blendedContainerContextImpl = project.in(file("blended.container.context.impl"))
//  .settings(commonSettings)
//  .dependsOn(
//    blendedContainerContextApi
//  )
//
//lazy val blendedMgmtBase = project.in(file("blended.mgmt.base"))
//  .settings(commonSettings)
//  .dependsOn(
//    blendedUtil,
//    blendedDomino,
//    blendedUpdaterConfigJvm
//  )
//
