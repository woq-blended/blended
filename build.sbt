import sbt.Keys._
import sbt._
import com.typesafe.sbt.osgi.SbtOsgi.autoImport._

val m2Repo = "file://" + System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository")

lazy val root = project
  .in(file("."))
  .settings(BuildHelper.defaultSettings:_*)
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
    blendedMgmtBase
  )

lazy val blendedUtil = BuildHelper.blendedOsgiProject(
  pName = "blended.util",
  pDescription = Some("Utility classes to use in other bundles."),
  exports = Seq("", "protocol")
).settings(
  libraryDependencies ++= Seq(
    Dependencies.akkaActor,
    Dependencies.slf4j,
    Dependencies.akkaTestkit % "test",
    Dependencies.akkaSlf4j % "test",
    Dependencies.scalatest % "test",
    Dependencies.junit % "test",
    Dependencies.logbackCore % "test",
    Dependencies.logbackClassic % "test"
  )
)

lazy val blendedTestsupport = BuildHelper.blendedProject(
  pName = "blended.testsupport",
  pDescription = Some("Some test helper classes.")
).settings(
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
).dependsOn(blendedUtil)

lazy val blendedUpdaterConfig = crossProject.in(file("blended.updater.config"))
  .settings(BuildHelper.defaultSettings:_*)
  .settings(
    name := "blended.updater.config",
    description := "Configurations for Updater and Launcher",

    libraryDependencies ++= Seq(
      Dependencies.prickle.organization %%% Dependencies.prickle.name % Dependencies.prickleVersion,
      Dependencies.scalatest.organization %%% Dependencies.scalatest.name % Dependencies.scalatestVersion % "test"
    )
  )
  .jvmSettings(BuildHelper.bundleSettings(
    symbolicName = "blended.updater.config",
    exports = Seq("", "json", "util", "/blended.launcher.config"),
    imports = Seq.empty
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

lazy val blendedLauncher = BuildHelper.blendedOsgiProject(
  pName = "blended.launcher",
  pDescription = Some("Provide an OSGi Launcher"),
  exports = Seq(""),
  imports = Seq("org.apache.commons.daemon;resolution:=optional", "de.tototec.cmdoption.*;resolution:=optional"),
  privates = Seq("jvmrunner", "runtime")
).settings(
  libraryDependencies ++= Seq(
    Dependencies.orgOsgi,
    Dependencies.cmdOption
  )
).dependsOn(blendedUpdaterConfigJvm)

lazy val blendedContainerContext = BuildHelper.blendedOsgiProject(
  pName = "blended.container.context",
  pDescription = Some("A simple OSGI service to provide access to the container's config directory."),
  exports = Seq(""),
  imports = Seq("blended.launcher.runtime;resolution:=optional")
).settings(
  OsgiKeys.bundleActivator := Some(name.value + ".internal.ContainerContextActivator"),
  libraryDependencies ++= Seq(
    Dependencies.typesafeConfig,
    Dependencies.slf4j,
    Dependencies.julToSlf4j,
    Dependencies.domino,
    Dependencies.orgOsgi,
    Dependencies.scalatest % "test",
    Dependencies.logbackCore % "test",
    Dependencies.logbackClassic % "test",
    Dependencies.mockitoAll % "test"
  )
).dependsOn(blendedUpdaterConfigJvm,blendedLauncher)

lazy val blendedDomino = BuildHelper.blendedOsgiProject(
  pName = "blended.domino",
  pDescription = Some("Blended Domino extension for new Capsule scopes."),
  exports = Seq("")
).settings(
  libraryDependencies ++= Seq(
    Dependencies.typesafeConfig
  )
).dependsOn(blendedContainerContext)

lazy val blendedMgmtBase = BuildHelper.blendedOsgiProject(
  pName = "blended.mgmt.base",
  pDescription = Some("Shared classes for management and reporting facility."),
  exports = Seq("", "json")
).settings(
  OsgiKeys.bundleActivator := Some(name.value + ".internal.MgmtActivator"),
  libraryDependencies ++= Seq(
    Dependencies.prickle,
  )
).dependsOn(blendedUtil, blendedDomino, blendedUpdaterConfigJvm)
