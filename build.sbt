import sbt._

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

  scalariformAutoformat := false,
  scalariformWithBaseDirectory := true,

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
  .settings(PublishConfig.noPublish)
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
    blendedJmsSampler,
    blendedTestsupportPojosr,
    blendedAkkaHttpApi,
    blendedAkkaHttp,
    blendedAkkaHttpJmsqueue,
    blendedAkkaHttpProxy,
    blendedAkkaHttpRestjms,
    blendedFile,
    blendedMgmtRepo,
    blendedSecurityAkkaHttp,
    blendedMgmtRepoRest,
    blendedPrickleAkkaHttp,
    blendedMgmtAgent,
    blendedPersistenceH2,
    blendedMgmtRest,
    blendedMgmtMock
  )

// TODO: Can we get rid of these ?
lazy val blendedUtilLogging = BlendedUtilLogging.project
lazy val blendedSecurityBoot = BlendedSecurityBoot.project
lazy val blendedContainerContextApi = BlendedContainerContextApi.project
lazy val blendedDomino = BlendedDomino.project
lazy val blendedUtil = BlendedUtil.project
lazy val blendedTestsupport = BlendedTestsupport.project
lazy val blendedAkka = BlendedAkka.project
lazy val blendedSecurityCross = BlendedSecurityCross.project
lazy val blendedSecurityJvm = BlendedSecurityJvm.project
lazy val blendedSecurityJs = BlendedSecurityJs.project
lazy val blendedUpdaterConfigCross = BlendedUpdaterConfigCross.project
lazy val blendedUpdaterConfigJs = BlendedUpdaterConfigJs.project
lazy val blendedUpdaterConfigJvm = BlendedUpdaterConfigJvm.project
lazy val blendedLauncher = BlendedLauncher.project
lazy val blendedMgmtBase = BlendedMgmtBase.project
lazy val blendedUpdater = BlendedUpdater.project
lazy val blendedUpdaterTools = BlendedUpdaterTools.project
lazy val blendedPersistence = BlendedPersistence.project
lazy val blendedUpdaterRemote = BlendedUpdaterRemote.project
lazy val blendedCamelUtils = BlendedCamelUtils.project
lazy val blendedJmsUtils = BlendedJmsUtils.project
lazy val blendedActivemqBrokerstarter = BlendedActivemqBrokerstarter.project
lazy val blendedContainerContextImpl = BlendedContainerContextImpl.project
lazy val blendedJmx = BlendedJmx.project
lazy val blendedJettyBoot = BlendedJettyBoot.project
lazy val blendedJmsSampler = BlendedJmsSampler.project
lazy val blendedTestsupportPojosr = BlendedTestsupportPojosr.project
lazy val blendedAkkaHttpApi = BlendedAkkaHttpApi.project
lazy val blendedAkkaHttp = BlendedAkkaHttp.project
lazy val blendedAkkaHttpJmsqueue = BlendedAkkaHttpJmsqueue.project
lazy val blendedAkkaHttpProxy = BlendedAkkaHttpProxy.project
lazy val blendedAkkaHttpRestjms = BlendedAkkaHttpRestjms.project
lazy val blendedFile = BlendedFile.project
lazy val blendedMgmtRepo = BlendedMgmtRepo.project
lazy val blendedSecurityAkkaHttp = BlendedSecurityAkkaHttp.project
lazy val blendedMgmtRepoRest = BlendedMgmtRepoRest.project
lazy val blendedPrickleAkkaHttp = BlendedPrickleAkkaHttp.project
lazy val blendedMgmtAgent = BlendedMgmtAgent.project
lazy val blendedPersistenceH2 = BlendedPersistenceH2.project
lazy val blendedMgmtRest = BlendedMgmtRest.project
lazy val blendedMgmtMock = BlendedMgmtMock.project
