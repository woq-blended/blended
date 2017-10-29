import sbt.Keys._
import sbt._

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
    blendedUpdaterConfigJvm
  )

lazy val blendedUtil = BuildHelper.blendedOsgiProject(
  pName = "blended.util",
  pDescription = Some("Utility classes to use in other bundles."),
  deps = Seq(
    Dependencies.akkaActor,
    Dependencies.slf4j,
    Dependencies.akkaTestkit % "test",
    Dependencies.akkaSlf4j % "test",
    Dependencies.scalatest % "test",
    Dependencies.junit % "test",
    Dependencies.logbackCore % "test",
    Dependencies.logbackClassic % "test"
  ),
  exports = Seq("", "protocol")
)

lazy val blendedTestsupport = BuildHelper.blendedProject(
  pName = "blended.testsupport",
  pDescription = Some("Some test helper classes."),
  deps = Seq(
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
