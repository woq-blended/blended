import sbt._
import de.wayofquality.sbt.jbake.JBake

//// this is required to use proper values in osgi manifest require capability
//val initSystemEarly: Unit = Option(System.getProperty("java.version"))
//  .map(v => v.split("[.]", 3).take(2).mkString("."))
//  .foreach(v => System.setProperty("java.version", v))

val travisBuildNumber = sys.env.getOrElse("TRAVIS_BUILD_NUMBER", "Not on Travis")

// A convenience to execute all tests in travis
addCommandAlias("ciBuild", "; clean ; test ")

// A convenience to push SNAPSHOT to sonatype Snapshots
addCommandAlias(name = "ciPublish", value="; clean ; packageBin ; publishSigned ")

// A convenience to package everything, sign it and push it to maven central
addCommandAlias("ciRelease", s"""; clean; packageBin ; sonatypeOpen "Auto Release via Travis ($travisBuildNumber)" ; publishSigned ; sonatypeClose ; sonatypeRelease""")

addCommandAlias("cleanPublish", "; coverageOff ; clean ; publishM2")
addCommandAlias("cleanCoverage", "; coverage ; clean ; test ; coverageReport ; coverageAggregate ; coverageOff")

addCommandAlias(name = "siteComplete", "; cleanCoverage ; unidoc ; jbakeSite")

inThisBuild(BuildHelper.readVersion(file("version.txt")))

lazy val global = Def.settings(
  Global/scalariformAutoformat := false,
  Global/scalariformWithBaseDirectory := true,

  Global/testlogDirectory := target.value / "testlog",

  Global/useGpg := false,
  Global/pgpPublicRing := baseDirectory.value / "project" / ".gnupg" / "pubring.gpg",
  Global/pgpSecretRing := baseDirectory.value / "project" / ".gnupg" / "secring.gpg",
  Global/pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray)
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
lazy val blendedMgmtServiceJmx = BlendedMgmtServiceJmx.project
lazy val blendedPrickle = BlendedPrickle.project
lazy val blendedSecurityLoginApi = BlendedSecurityLoginApi.project
lazy val blendedMgmtWs = BlendedMgmtWs.project
lazy val blendedSecurityLoginImpl = BlendedSecurityLoginImpl.project
lazy val blendedSecurityLoginRest = BlendedSecurityLoginRest.project
lazy val blendedSecurityTest = BlendedSecurityTest.project
lazy val blendedSecuritySsl = BlendedSecuritySsl.project
lazy val blendedHawtioLogin = BlendedHawtioLogin.project
lazy val blendedJolokia = BlendedJolokia.project
lazy val blendedSamplesCamel = BlendedSamplesCamel.project
lazy val blendedSamplesJms = BlendedSamplesJms.project
lazy val blendedAkkaHttpSampleHelloworld = BlendedAkkaHttpSampleHelloworld.project
// Referenced in adoc file: doc/content/BUILDING.adoc
// tag::Building[]
lazy val blendedActivemqClient = BlendedActivemqClient.project
// end::Building[]
lazy val blendedSecurityScep = BlendedSecurityScep.project
lazy val blendedSecurityScepStandalone = BlendedSecurityScepStandalone.project
lazy val blendedJmsBridge = BlendedJmsBridge.project
lazy val blendedStreams = BlendedStreams.project
lazy val blendedStreamsDispatcher = BlendedStreamsDispatcher.project
lazy val blendedStreamsTestsupport = BlendedStreamsTestsupport.project
lazy val blendedDocs = BlendedDocsJs.project
lazy val blendedDependencies = BlendedDependencies.project

lazy val jvmProjects : Seq[ProjectReference] = Seq(
  blendedUtilLogging,
  blendedSecurityBoot,
  blendedContainerContextApi,
  blendedDomino,
  blendedUtil,
  blendedTestsupport,
  blendedAkka,
  blendedSecurityJvm,
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
  blendedMgmtMock,
  blendedMgmtServiceJmx,
  blendedPrickle,
  blendedSecurityLoginApi,
  blendedMgmtWs,
  blendedSecurityLoginImpl,
  blendedSecurityLoginRest,
  blendedSecurityTest,
  blendedSecuritySsl,
  blendedSecurityScep,
  blendedSecurityScepStandalone,
  blendedHawtioLogin,
  blendedJolokia,
  blendedSamplesCamel,
  blendedSamplesJms,
  blendedAkkaHttpSampleHelloworld,
  blendedActivemqClient,
  blendedJmsBridge,
  blendedStreams,
  blendedStreamsDispatcher,
  blendedStreamsTestsupport
)

lazy val jsProjects : Seq[ProjectReference] = Seq(
  blendedSecurityJs,
  blendedUpdaterConfigJs,
  blendedDocs
)

lazy val root = {
  project
    .in(file("."))
    .settings(
      name := "blended",
      // exclude JS projects from scaladoc
      unidocProjectFilter.in(ScalaUnidoc, unidoc) := inAnyProject -- inProjects(jsProjects:_*)
    )
    .settings(global)
    .settings(CommonSettings())
    .settings(PublishConfig.doPublish)
    .enablePlugins(ScalaUnidocPlugin, JBake)
    .settings(RootSettings(BlendedDocsJs.project))
    // .settings(ExportDependencies.settings)
    .aggregate((jvmProjects ++ jsProjects ++ Seq[ProjectReference](BlendedDependencies.project)):_*)
}
