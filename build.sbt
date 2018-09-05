import java.nio.file.{CopyOption, Files, StandardCopyOption}

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
  publishMavenStyle := true
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
//    blendedContainerContextImpl,
//    blendedMgmtBase,
  )

lazy val blendedUtilLogging = project.in(file("blended.util.logging"))
  .settings(BlendedUtilLogging.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedSecurityBoot = project.in(file("blended.security.boot"))
  .settings(BlendedSecurityBoot.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedContainerContextApi = project.in(file("blended.container.context.api"))
  .settings(BlendedContainerContextApi.settings)
  .dependsOn(blendedUtilLogging)
  .enablePlugins(SbtOsgi)

lazy val blendedDomino = project.in(file("blended.domino"))
  .settings(BlendedDomino.settings)
  .dependsOn(blendedContainerContextApi)
  .enablePlugins(SbtOsgi)

lazy val blendedUtil = project.in(file("blended.util"))
  .settings(BlendedUtil.settings)
  .enablePlugins(SbtOsgi)

lazy val blendedTestsupport = project.in(file("blended.testsupport"))
  .settings(BlendedTestsupport.settings)
  .dependsOn(blendedUtil, blendedUtilLogging, blendedSecurityBoot)

lazy val blendedAkka = project.in(file("blended.akka"))
  .settings(BlendedAkka.settings)
  .dependsOn(blendedUtilLogging, blendedContainerContextApi, blendedDomino)
  .enablePlugins(SbtOsgi)

lazy val blendedSecurity = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
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


lazy val blendedLauncher = project.in(file("blended.launcher"))
  .settings(BlendedLauncher.settings)
  .settings(
    Test/resourceGenerators += Def.task {

      val frameworks : Seq[ModuleID] = Seq(
        "org.apache.felix" % "org.apache.felix.framework" % "5.0.0",
        "org.apache.felix" % "org.apache.felix.framework" % "5.6.10",

        "org.eclipse" % "org.eclipse.osgi" % "3.8.0.v20120529-1548",
        "org.osgi" % "org.eclipse.osgi" % "3.10.100.v20150529-1857",
        "org.eclipse.platform" % "org.eclipse.osgi" % "3.12.50",
        "org.eclipse.birt.runtime" % "org.eclipse.osgi" % "3.9.1.v20130814-1242",
        "org.eclipse.birt.runtime" % "org.eclipse.osgi" % "3.10.0.v20140606-1445"
      )

      val osgiDir = target.value / "test-osgi"

      BuildHelper.deleteRecursive(osgiDir)
      Files.createDirectories(osgiDir.toPath)

      val files = frameworks
        .map{ mid => BuildHelper.resolveModuleFile(mid, target.value) }
        .collect {
          case f if !f.isEmpty => f
        }
        .flatten

      files.map { f =>
        val tf = new File(osgiDir, f.getName)
        Files.copy(f.toPath, tf.toPath, StandardCopyOption.REPLACE_EXISTING)
        tf
      }
    }.taskValue
  )
  .dependsOn(blendedUtilLogging, blendedUpdaterConfigJVM, blendedTestsupport % "test")
  .enablePlugins(SbtOsgi, UniversalPlugin, UniversalDeployPlugin)

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