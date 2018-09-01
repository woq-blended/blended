import sbt._

// this is required to use proper values in osgi manifest require capability
val initSystemEarly = Option(System.getProperty("java.version"))
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
  // essential to not try to compile pom.scala files
  sourcesInBase := false,
  publishMavenStyle := true
))

// General settings for subprojects to be published
lazy val doPublish = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
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
    unidocProjectFilter.in(ScalaUnidoc, unidoc) := inAnyProject // -- inProjects(blendedUpdaterConfigJs)
  )
  .settings(noPublish)
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(
    blendedUtilLogging,
    blendedSecurityBoot
//    blendedUtil
//    blendedTestsupport,
//    blendedUpdaterConfigJs,
//    blendedUpdaterConfigJvm,
//    blendedLauncher,
//    blendedContainerContextApi,
//    blendedContainerContextImpl,
//    blendedDomino,
//    blendedMgmtBase,
//    blendedAkka
  )

lazy val blendedUtilLogging = project.in(file("blended.util.logging"))
  .settings(doPublish)
  .settings(BlendedUtilLogging.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedSecurityBoot = project.in(file("blended.security.boot"))
  .settings(doPublish)
  .settings(BlendedSecurityBoot.settings)
  .enablePlugins(SbtOsgi)

//lazy val blendedUtil = project.in(file("blended.util"))

//lazy val blendedSecurityBoot =  project.in(file("blended.security.boot"))
//  .settings(commonSettings)
//
//lazy val blendedTestsupport = project.in(file("blended.testsupport"))
//  .settings(commonSettings)
//  .dependsOn(blendedUtil, blendedSecurityBoot)
//
//lazy val blendedUpdaterConfig = crossProject.in(file("blended.updater.config"))
//  //  .enablePlugins(BlendedPlugin)
//  .settings(commonSettings,
//  libraryDependencies ++= Seq(
//    Dependencies.prickle.organization %%% Dependencies.prickle.name % Dependencies.prickleVersion,
//    Dependencies.scalatest.organization %%% Dependencies.scalatest.name % Dependencies.scalatestVersion % "test"
//  )
//)
//  .jvmSettings(BuildHelper.bundleSettings(
//    exportPkgs = Seq("", "json", "util", "/blended.launcher.config"),
//    importPkgs = Seq.empty
//  ): _*)
//  .jvmSettings(
//    unmanagedResourceDirectories in Compile += baseDirectory.value / "src" / "main" / "binaryResources",
//    unmanagedResourceDirectories in Test += baseDirectory.value / "src" / "test" / "binaryResources",
//    javaOptions in Test += ("-DprojectTestOutput=" + target.value / s"scala-${scalaBinaryVersion.value}" / "test-classes"),
//    fork in Test := true,
//    libraryDependencies ++= Seq(
//      Dependencies.typesafeConfig,
//      Dependencies.slf4j,
//      Dependencies.scalatest % "test",
//      Dependencies.logbackCore % "test",
//      Dependencies.logbackClassic % "test"
//    )
//  )
//
//lazy val blendedUpdaterConfigJvm = blendedUpdaterConfig.jvm
//  .dependsOn(blendedTestsupport % "test")
//  .enablePlugins(SbtOsgi)
//
//lazy val blendedUpdaterConfigJs = blendedUpdaterConfig.js
//
//lazy val blendedLauncher = project.in(file("blended.launcher"))
//  .settings(commonSettings)
//  .dependsOn(blendedUpdaterConfigJvm)
//
//lazy val blendedContainerContextApi = project.in(file("blended.container.context.api"))
//  .settings(commonSettings)
//  .dependsOn(
//    blendedUpdaterConfigJvm,
//    blendedLauncher
//  )
//
//lazy val blendedContainerContextImpl = project.in(file("blended.container.context.impl"))
//  .settings(commonSettings)
//  .dependsOn(
//    blendedContainerContextApi
//  )
//
//lazy val blendedDomino = project.in(file("blended.domino"))
//  .settings(commonSettings)
//  .dependsOn(blendedContainerContextApi)
//
//lazy val blendedMgmtBase = project.in(file("blended.mgmt.base"))
//  .settings(commonSettings)
//  .dependsOn(
//    blendedUtil,
//    blendedDomino,
//    blendedUpdaterConfigJvm
//  )
//
//lazy val blendedAkka = project.in(file("blended.akka"))
//  .settings(commonSettings)
//  .dependsOn(
//    blendedContainerContextApi,
//    blendedDomino
//  )
