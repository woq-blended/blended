implicit val scalaVersion = ScalaVersion(BlendedVersions.scalaVersion)
val scalaJsBinVersion = ScalaVersion(BlendedVersions.scalaJsVersion).binaryVersion

// Dependencies

val activeMqBroker = "org.apache.activemq" % "activemq-broker" % BlendedVersions.activeMqVersion
val activeMqClient = "org.apache.activemq" % "activemq-client" % BlendedVersions.activeMqVersion
val activeMqSpring = "org.apache.activemq" % "activemq-spring" % BlendedVersions.activeMqVersion
val activeMqOsgi = "org.apache.activemq" % "activemq-osgi" % BlendedVersions.activeMqVersion
val activeMqKahadbStore = "org.apache.activemq" % "activemq-kahadb-store" % BlendedVersions.activeMqVersion
    
val akkaActor = "com.typesafe.akka" %% "akka-actor" % BlendedVersions.akkaVersion
val akkaCamel = "com.typesafe.akka" %% "akka-camel" % BlendedVersions.akkaVersion
val akkaOsgi = "com.typesafe.akka" %% "akka-osgi" % BlendedVersions.akkaVersion
val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % BlendedVersions.akkaVersion
val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % BlendedVersions.akkaVersion

val apacheShiroCore = "org.apache.shiro" % "shiro-core" % BlendedVersions.apacheShiroVersion
val apacheShiroWeb = "org.apache.shiro" % "shiro-web" % BlendedVersions.apacheShiroVersion

val bndLib = "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.2.0"

val camelCore = "org.apache.camel" % "camel-core" % BlendedVersions.camelVersion
val camelJms = "org.apache.camel" % "camel-jms" % BlendedVersions.camelVersion
val camelHttp = "org.apache.camel" % "camel-http" % BlendedVersions.camelVersion
val camelServlet = "org.apache.camel" % "camel-servlet" % BlendedVersions.camelVersion
val commonsDaemon = "commons-daemon" % "commons-daemon" % "1.0.15"
val commonsExec = "org.apache.commons" % "commons-exec" % "1.3"
val commonsLang = "commons-lang" % "commons-lang" % "2.6"
val commonsNet = "commons-net" % "commons-net" % "3.3"
val commonsPool = "commons-pool" % "commons-pool" % "1.6"
val cmdOption = "de.tototec" % "de.tototec.cmdoption" % "0.4.2"
val concurrentLinkedHashMapLru = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"
    
val domino = "com.github.domino-osgi" %% "domino" % "1.1.1"

val felixConfigAdmin = "org.apache.felix" % "org.apache.felix.configadmin" % "${felix.ca.version}"
val felixEventAdmin = "org.apache.felix" % "org.apache.felix.eventadmin" % "${felix.event.version}"
val felixFramework = "org.apache.felix" % "org.apache.felix.framework" % "5.0.0"
val felixFileinstall = "org.apache.felix" % "org.apache.felix.fileinstall" % "3.4.2"
val felixGogoCommand = "org.apache.felix" % "org.apache.felix.gogo.command" % "0.14.0"
val felixGogoShell = "org.apache.felix" % "org.apache.felix.gogo.shell" % "0.10.0"
val felixGogoRuntime = "org.apache.felix" % "org.apache.felix.gogo.runtime" % "0.16.2"
val felixMetatype = "org.apache.felix" % "org.apache.felix.metatype" % "1.0.12"

val geronimoJms11Spec = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"
val geronimoServlet25Spec = "org.apache.geronimo.specs" % "geronimo-servlet_2.5_spec" % "1.2"
val geronimoServlet30Spec = "org.apache.geronimo.specs" % "geronimo-servlet_3.0_spec" % "1.0"
    
val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % BlendedVersions.slf4jVersion
val jms11Spec = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"
val jsonLenses = "net.virtual-void" %% "json-lenses" % "0.5.4"
val junit = "junit" % "junit" % "4.11"
val julToSlf4j = "org.slf4j" % "jul-to-slf4j" % BlendedVersions.slf4jVersion

val lambdaTest = "de.tototec" % "de.tobiasroeser.lambdatest" % "0.2.4"
val logbackCore = "ch.qos.logback" % "logback-core" % "1.1.3"
val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.3"

val mockitoAll = "org.mockito" % "mockito-all" % "1.9.5"

val orientDbCore = "com.orientechnologies" % "orientdb-core" % "2.2.7"
val orgOsgi = "org.osgi" % "org.osgi.core" % "5.0.0"
val orgOsgiCompendium = "org.osgi" % "org.osgi.compendium" % "5.0.0"

val scalaLib = "org.scala-lang" % "scala-library" % BlendedVersions.scalaVersion
val scalaReflect = "org.scala-lang" % "scala-reflect" % BlendedVersions.scalaVersion
val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4"
val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.5"
val slf4j = "org.slf4j" % "slf4j-api" % BlendedVersions.slf4jVersion
val slf4jLog4j12 = "org.slf4j" % "slf4j-log4j12" % BlendedVersions.slf4jVersion
val sprayClient = "io.spray" %% "spray-client" % BlendedVersions.sprayVersion
val sprayCaching = "io.spray" %% "spray-caching" % BlendedVersions.sprayVersion
val sprayHttp = "io.spray" %% "spray-http" % BlendedVersions.sprayVersion
val sprayHttpx = "io.spray" %% "spray-httpx" % BlendedVersions.sprayVersion
val sprayIo = "io.spray" %% "spray-io" % BlendedVersions.sprayVersion
val sprayJson = "io.spray" %% "spray-json" % BlendedVersions.sprayVersion
val sprayRouting = "io.spray" %% "spray-routing" % BlendedVersions.sprayVersion
val sprayServlet = "io.spray" %% "spray-servlet" % BlendedVersions.sprayVersion
val sprayTestkit = "io.spray" %% "spray-testkit" % BlendedVersions.sprayVersion
val sprayUtil = "io.spray" %% "spray-util" % BlendedVersions.sprayVersion
val shapeless = "com.chuusai" %% "shapeless" % "1.2.4"

val typesafeConfig = "com.typesafe" % "config" % "1.2.1"

val wiremock = "com.github.tomakehurst" % "wiremock" % "2.1.11"
val wiremockStandalone = "com.github.tomakehurst" % "wiremock-standalone" % "2.1.11"

// Blended Projects

object BlendedModule {
  def apply(name : String) = BlendedVersions.blendedGroupId % name % BlendedVersions.blendedVersion
}

val blendedParent = Parent(
  gav = BlendedModule("blended.parent"),
  relativePath = "../blended.parent"
)

val blendedActivemqBrokerstarter = BlendedModule("blended.activemq.brokerstarter")
val blendedActivemqClient = BlendedModule("blended.activemq.client")
val blendedActivemqDefaultbroker = BlendedModule("blended.activemq.defaultbroker")
val blendedAkka = BlendedModule("blended.akka")
val blendedAkkaItest = BlendedModule("blended.akka.itest")
val blendedCamelUtils = BlendedModule("blended.camel.utils")
val blendedContainerContext = BlendedModule("blended.container.context")
val blendedContainerRegistry = BlendedModule("blended.container.registry")
val blendedDemoLauncher = BlendedModule("blended.demo.launcher")
val blendedDemoMgmt = BlendedModule("blended.demo.mgmt")
val blendedDockerReactor = BlendedModule("blended.docker.reactor")
val blendedDockerLauncherDemo = BlendedModule("blended.docker.launcher.demo")
val blendedDomino = BlendedModule("blended.domino")
val blendedHawtioLogin = BlendedModule("blended.hawtio.login")
val blendedItestSupport = BlendedModule("blended.itestsupport")
val blendedJmsUtils = BlendedModule("blended.jms.utils")
val blendedJmx = BlendedModule("blended.jmx")
val blendedJolokia = BlendedModule("blended.jolokia")
val blendedLauncher = BlendedModule("blended.launcher")
val blendedLauncherFeatures = BlendedModule("blended.launcher.features")
val blendedMgmtAgent = BlendedModule("blended.mgmt.agent")
val blendedMgmtBase = BlendedModule("blended.mgmt.base")
val blendedMgmtRepo = BlendedModule("blended.mgmt.repo")
val blendedMgmtRepoRest = BlendedModule("blended.mgmt.repo.rest")
val blendedMgmtMock = BlendedModule("blended.mgmt.mock")
val blendedMgmtRest = BlendedModule("blended.mgmt.rest")
val blendedMgmtUi = BlendedModule("blended.mgmt.ui")
val blendedPersistence = BlendedModule("blended.persistence")
val blendedPersistenceOrient = BlendedModule("blended.persistence.orient")
val blendedSamplesReactor = BlendedModule("blended.samples.reactor")
val blendedSamplesCamel = BlendedModule("blended.samples.camel")
val blendedSamplesJms = BlendedModule("blended.samples.jms")
val blendedSamplesSprayHelloworld = BlendedModule("blended.samples.spray.helloworld")
val blendedSecurity = BlendedModule("blended.security")
val blendedSecurityBoot = BlendedModule("blended.security.boot")
val blendedSpray = BlendedModule("blended.spray")
val blendedSprayApi = BlendedModule("blended.spray.api")
val blendedTestSupport = BlendedModule("blended.testsupport")
val blendedUpdater = BlendedModule("blended.updater")
val blendedUpdaterConfig = BlendedModule("blended.updater.config")
val blendedUpdaterMavenPlugin = BlendedModule("blended-updater-maven-plugin")
val blendedUpdaterRemote = BlendedModule("blended.updater.remote")
val blendedUpdaterTools = BlendedModule("blended.updater.tools")
val blendedUtil = BlendedModule("blended.util")

