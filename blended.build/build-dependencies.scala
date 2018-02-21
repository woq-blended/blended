implicit val scalaVersion = ScalaVersion(BlendedVersions.scalaVersion)

case class ScalaJsVersion(val version: String) {
  val binaryVersion = version.split("\\.", 3).take(2).mkString(".")
}

implicit val implicitScalaJsVersion = ScalaJsVersion(BlendedVersions.scalaJsVersion)

implicit class ScalaJsGroupId(groupId: String) {
  def %%%(artifactId: String)(implicit scalaVersion: ScalaVersion, scalaJsVersion: ScalaJsVersion): GroupArtifactId = {
    groupId %% (artifactId + "_sjs" + scalaJsVersion.binaryVersion)
  }
}

/** Helper class, to improve writing dependency experience. */
implicit class RichDependency(d: Dependency) {
  def copy(
    gav: Gav = d.gav,
    `type`: String = d.`type`,
    classifier: Option[String] = d.classifier,
    scope: Option[String] = d.scope,
    systemPath: Option[String] = d.systemPath,
    exclusions: scala.collection.immutable.Seq[GroupArtifactId] = d.exclusions,
    optional: Boolean = d.optional): Dependency =
    new Dependency(gav, `type`, classifier, scope, systemPath, exclusions, optional)

  def %(scope: String): Dependency = d.copy(scope = Option(scope).filter(!_.trim().isEmpty()))

  def classifier(classifier: String): Dependency = copy(classifier = Option(classifier))

  def pure: Dependency = copy(exclusions = Seq("*" % "*"))

  def exclude(ga: GroupArtifactId): Dependency = copy(exclusions = d.exclusions ++ Seq(ga))
}

// Dependencies
object Deps {
  val activationApi = "org.apache.servicemix.specs" % "org.apache.servicemix.specs.activation-api-1.1" % "2.2.0"

  val activeMqBroker = "org.apache.activemq" % "activemq-broker" % BlendedVersions.activeMqVersion
  val activeMqClient = "org.apache.activemq" % "activemq-client" % BlendedVersions.activeMqVersion
  val activeMqSpring = "org.apache.activemq" % "activemq-spring" % BlendedVersions.activeMqVersion
  val activeMqOsgi = "org.apache.activemq" % "activemq-osgi" % BlendedVersions.activeMqVersion
  val activeMqKahadbStore = "org.apache.activemq" % "activemq-kahadb-store" % BlendedVersions.activeMqVersion

  def akka(m: String) = "com.typesafe.akka" %% s"akka-${m}" % BlendedVersions.akkaVersion
  def akka_Http(m: String) = "com.typesafe.akka" %% s"akka-${m}" % "10.0.11"
  val akkaActor = akka("actor")
  val akkaCamel = akka("camel")
  val akkaHttp = akka_Http("http")
  val akkaHttpCore = akka_Http("http-core")
  val akkaHttpTestkit = akka_Http("http-testkit")
  val akkaParsing = akka_Http("parsing")
  val akkaOsgi = akka("osgi")
  val akkaStream = akka("stream")
  val akkaTestkit = akka("testkit")
  val akkaSlf4j = akka("slf4j")

  val aopAlliance = "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.aopalliance" % "1.0_6"

  val apacheShiroCore = "org.apache.shiro" % "shiro-core" % BlendedVersions.apacheShiroVersion
  val apacheShiroWeb = "org.apache.shiro" % "shiro-web" % BlendedVersions.apacheShiroVersion

  val ariesBlueprintApi = "org.apache.aries.blueprint" % "org.apache.aries.blueprint.api" % "1.0.1"
  val ariesBlueprintCore = "org.apache.aries.blueprint" % "org.apache.aries.blueprint.core" % "1.4.3"
  val ariesJmxApi = "org.apache.aries.jmx" % "org.apache.aries.jmx.api" % "1.1.1"
  val ariesJmxCore = "org.apache.aries.jmx" % "org.apache.aries.jmx.core" % "1.1.1"
  val ariesProxyApi = "org.apache.aries.proxy" % "org.apache.aries.proxy.api" % "1.0.1"
  val ariesUtil = "org.apache.aries" % "org.apache.aries.util" % "1.1.0"

  val asmAll = "org.ow2.asm" % "asm-all" % "4.1"
  val bndLib = "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.2.0"

  val bouncyCastlePkix = "org.bouncycastle" % "bcpkix-jdk15on" % "1.59"
  val bouncyCastleBcprov = "org.bouncycastle" % "bcprov-jdk15on" % "1.59"

  val camelCore = "org.apache.camel" % "camel-core" % BlendedVersions.camelVersion
  val camelJms = "org.apache.camel" % "camel-jms" % BlendedVersions.camelVersion
  val camelSpring = "org.apache.camel" % "camel-spring" % BlendedVersions.camelVersion

  val commonsBeanUtils = "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.commons-beanutils" % "1.8.3_2"
  val commonsCodec = "org.apache.commons" % "com.springsource.org.apache.commons.codec" % "1.6.0"
  val commonsCompress = "org.apache.commons" % "commons-compress" % "1.13"
  val commonsCollections = "org.apache.commons" % "com.springsource.org.apache.commons.collections" % "3.2.1"
  val commonsDaemon = "commons-daemon" % "commons-daemon" % "1.0.15"
  val commonsDiscovery = "org.apache.commons" % "com.springsource.org.apache.commons.discovery" % "0.4.0"
  val commonsExec = "org.apache.commons" % "commons-exec" % "1.3"
  val commonsHttpclient = "org.apache.commons" % "com.springsource.org.apache.commons.httpclient" % "3.1.0"
  val commonsIo = "org.apache.commons" % "com.springsource.org.apache.commons.io" % "1.4.0"
  val commonsLang = "commons-lang" % "commons-lang" % "2.6"
  val commonsNet = "commons-net" % "commons-net" % "3.3"
  val commonsPool = "commons-pool" % "commons-pool" % "1.6"

  val cmdOption = "de.tototec" % "de.tototec.cmdoption" % "0.4.2"
  val concurrentLinkedHashMapLru = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"

  val domino = "com.github.domino-osgi" %% "domino" % "1.1.2"
  val dockerJava = "com.github.docker-java" % "docker-java" % BlendedVersions.dockerJavaVersion

  // provides Equinox console commands to gogo shell
  val eclipseEquinoxConsole = "org.eclipse.platform" % "org.eclipse.equinox.console" % "1.1.300"
  val eclipseOsgi = "org.eclipse.platform" % "org.eclipse.osgi" % "3.12.50"

  val felixConfigAdmin = "org.apache.felix" % "org.apache.felix.configadmin" % "1.8.6"
  val felixConnect = "org.apache.felix" % "org.apache.felix.connect" % "0.1.0"
  val felixEventAdmin = "org.apache.felix" % "org.apache.felix.eventadmin" % "1.3.2"
  val felixFramework = "org.apache.felix" % "org.apache.felix.framework" % "5.6.10"
  val felixFileinstall = "org.apache.felix" % "org.apache.felix.fileinstall" % "3.4.2"
  val felixGogoCommand = "org.apache.felix" % "org.apache.felix.gogo.command" % "0.14.0"
  val felixGogoShell = "org.apache.felix" % "org.apache.felix.gogo.shell" % "0.10.0"
  val felixGogoRuntime = "org.apache.felix" % "org.apache.felix.gogo.runtime" % "0.16.2"
  val felixHttpApi = "org.apache.felix" % "org.apache.felix.http.api" % "3.0.0"
  val felixHttpJetty = "org.apache.felix" % "org.apache.felix.http.jetty" % "3.0.0"
  val felixMetatype = "org.apache.felix" % "org.apache.felix.metatype" % "1.0.12"

  val geronimoAnnotation = "org.apache.geronimo.specs" % "geronimo-annotation_1.1_spec" % "1.0.1"
  val geronimoJaspic = "org.apache.geronimo.specs" % "geronimo-jaspic_1.0_spec" % "1.1"
  val geronimoJ2eeMgmtSpec = "org.apache.geronimo.specs" % "geronimo-j2ee-management_1.1_spec" % "1.0.1"
  val geronimoJms11Spec = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"
  val geronimoServlet25Spec = "org.apache.geronimo.specs" % "geronimo-servlet_2.5_spec" % "1.2"
  val geronimoServlet30Spec = "org.apache.geronimo.specs" % "geronimo-servlet_3.0_spec" % "1.0"

  val hawtioWeb = Dependency(gav = "io.hawt" % "hawtio-web" % "1.4.65", `type` = "war")

  val javaxEl = "javax.el" % "javax.el-api" % "3.0.1-b04"
  val javaxMail = "javax.mail" % "mail" % "1.4.5"
  val javaxServlet31 = "org.everit.osgi.bundles" % "org.everit.osgi.bundles.javax.servlet.api" % "3.1.0"

  val jacksonCore = "com.fasterxml.jackson.core" % "jackson-core" % "2.9.3"
  val jacksonBind = "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.3"
  val jacksonAnnotations = "com.fasterxml.jackson.core" % "jackson-annotations" % "2.9.3"
  val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % BlendedVersions.slf4jVersion

  private def jetty(n: String) = "org.eclipse.jetty" % s"jetty-${n}" % BlendedVersions.jettyVersion
  private def jettyOsgi(n: String) = "org.eclipse.jetty.osgi" % s"jetty-${n}" % BlendedVersions.jettyVersion
  val jettyDeploy = jetty("deploy")
  val jettyHttp = jetty("http")
  val jettyIo = jetty("io")
  val jettyJmx = jetty("jmx")
  val jettySecurity = jetty("security")
  val jettyServlet = jetty("servlet")
  val jettyServer = jetty("server")
  val jettyUtil = jetty("util")
  val jettyWebapp = jetty("webapp")
  val jettyXml = jetty("xml")

  val jettyOsgiBoot = jettyOsgi("osgi-boot")
  val jettyHttpService = jettyOsgi("httpservice")
  val equinoxServlet = "org.eclipse.platform" % "org.eclipse.equinox.http.servlet" % "1.4.0"

  val jjwt = "io.jsonwebtoken" % "jjwt" % "0.7.0"
  val jms11Spec = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"
  val jsonLenses = "net.virtual-void" %% "json-lenses" % "0.5.4"
  val jolokiaJvm = "org.jolokia" % "jolokia-jvm" % BlendedVersions.jolokiaVersion
  val jolokiaJvmAgent = Dependency(
    jolokiaJvm,
    classifier = "agent"
  )
  val juliOverSlf4j = "com.github.akiraly.reusable-poms" % "tomcat-juli-over-slf4j" % "4"
  val junit = "junit" % "junit" % "4.11"
  val julToSlf4j = "org.slf4j" % "jul-to-slf4j" % BlendedVersions.slf4jVersion
  val jsr305 = "com.google.code.findbugs" % "jsr305" % "3.0.1"

  val lambdaTest = "de.tototec" % "de.tobiasroeser.lambdatest" % "0.2.4"
  val logbackCore = "ch.qos.logback" % "logback-core" % "1.2.3"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val log4s = "org.log4s" %% "log4s" % "1.4.0"

  val mockitoAll = "org.mockito" % "mockito-all" % "1.9.5"
  val microjson = "com.github.benhutchison" %% "microjson" % "1.4"

  val paxSwissboxCore = "org.ops4j.pax.swissbox" % "pax-swissbox-core" % "1.7.0"
  val paxSwissboxOptJcl = "org.ops4j.pax.swissbox" % "pax-swissbox-optional-jcl" % "1.7.0"
  val prickle = "com.github.benhutchison" %% "prickle" % BlendedVersions.prickle

  val ops4jBaseLang = "org.ops4j.base" % "ops4j-base-lang" % "1.4.0"

  val orientDbCore = "com.orientechnologies" % "orientdb-core" % "2.2.7"
  val orgOsgi = "org.osgi" % "org.osgi.core" % "6.0.0"
  val orgOsgiCompendium = "org.osgi" % "org.osgi.compendium" % "5.0.0"

  //  private def paxweb(a: String) = "org.ops4j.pax.web" % s"pax-web-${a}" % BlendedVersions.paxWeb
  //  val paxwebApi = paxweb("api")
  //  val paxwebDescriptor = paxweb("descriptor")
  //  val paxwebExtWhiteboard = paxweb("extender-whiteboard")
  //  val paxwebExtWar = paxweb("extender-war")
  //  val paxwebJetty = paxweb("jetty")
  //  val paxwebJsp = paxweb("jsp")
  //  val paxwebRuntime = paxweb("runtime")
  //  val paxwebSpi = paxweb("spi")

  val reactiveStreams = "org.reactivestreams" % "reactive-streams" % "1.0.0.final"

  val scalaCompatJava8 = "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"
  val scalaLib = "org.scala-lang" % "scala-library" % BlendedVersions.scalaVersion
  val scalaParser = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.6"
  val scalaReflect = "org.scala-lang" % "scala-reflect" % BlendedVersions.scalaVersion
  val scalaTest = "org.scalatest" %% "scalatest" % BlendedVersions.scalaTestVersion
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.6"

  val scep = "com.google.code.jscep" % "jscep" % "2.5.0"

  val servicemixJaxbApi = "org.apache.servicemix.specs" % "org.apache.servicemix.specs.jaxb-api-2.2" % "2.5.0"
  val servicemixJaxbImpl = "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.jaxb-impl" % "2.2.1.1_2"
  val servicemixJaxbRuntime = "org.jvnet.jaxb2_commons" % "jaxb2-basics-runtime" % "0.6.4"
  val servicemixStaxApi = "org.apache.servicemix.specs" % "org.apache.servicemix.specs.stax-api-1.0" % "2.4.0"

  val shiroCore = "org.apache.shiro" % "shiro-core" % BlendedVersions.apacheShiroVersion
  val shiroWeb = "org.apache.shiro" % "shiro-web" % BlendedVersions.apacheShiroVersion

  val slf4j = "org.slf4j" % "slf4j-api" % BlendedVersions.slf4jVersion
  val slf4jJcl = "org.slf4j" % "jcl-over-slf4j" % BlendedVersions.slf4jVersion
  val slf4jJul = "org.slf4j" % "jul-to-slf4j" % BlendedVersions.slf4jVersion
  val slf4jLog4j12 = "org.slf4j" % "slf4j-log4j12" % BlendedVersions.slf4jVersion

  private def spray(n: String) = "io.spray" %% s"spray-${n}" % BlendedVersions.sprayVersion
  val sprayClient = spray("client")
  val sprayCaching = spray("caching")
  val sprayHttp = spray("http")
  val sprayHttpx = spray("httpx")
  val sprayIo = spray("io")
  val sprayJson = spray("json")
  val sprayRouting = spray("routing")
  val sprayServlet = spray("servlet")
  val sprayTestkit = spray("testkit")
  val sprayUtil = spray("util")

  private def spring(n: String) = "org.apache.servicemix.bundles" % s"org.apache.servicemix.bundles.spring-${n}" % BlendedVersions.springVersion
  val springBeans = spring("beans")
  val springAop = spring("aop")
  val springContext = spring("context")
  val springContextSupport = spring("context-support")
  val springExpression = spring("expression")
  val springCore = spring("core")
  val springJms = spring("jms")
  val springTx = spring("tx")

  val shapeless = "com.chuusai" %% "shapeless" % BlendedVersions.shapelessVersion

  val typesafeConfig = "com.typesafe" % "config" % "1.3.1"
  val typesafeConfigSSL = "com.typesafe" %% "ssl-config-core" % "0.2.2"

  val wiremock = "com.github.tomakehurst" % "wiremock" % "2.1.11"
  val wiremockStandalone = "com.github.tomakehurst" % "wiremock-standalone" % "2.1.11"

  private def xbean(n: String) = "org.apache.xbean" % s"xbean-${n}" % BlendedVersions.xbean
  val xbeanAsmShaded = xbean("asm6-shaded")
  val xbeanBundleUtils = xbean("bundleutils")
  val xbeanFinder = xbean("finder-shaded")
  val xbeanReflect = xbean("reflect")
  val xbeanSpring = xbean("spring")
}

// convenience and backward compatibility
import Deps._

// Blended Projects

object BlendedModule {
  def apply(name: String) = BlendedVersions.blendedGroupId % name % BlendedVersions.blendedVersion
}

val blendedActivemqBrokerstarter = BlendedModule("blended.activemq.brokerstarter")
val blendedActivemqClient = BlendedModule("blended.activemq.client")
val blendedActivemqDefaultbroker = BlendedModule("blended.activemq.defaultbroker")
val blendedAkka = BlendedModule("blended.akka")
val blendedAkkaHttp = BlendedModule("blended.akka.http")
val blendedAkkaHttpSampleHelloworld = BlendedModule("blended.akka.http.sample.helloworld")
val blendedCamelUtils = BlendedModule("blended.camel.utils")
val blendedContainerContext = BlendedModule("blended.container.context")
val blendedContainerRegistry = BlendedModule("blended.container.registry")
val blendedDemoReactor = BlendedModule("blended.demo.reactor")
val blendedDemoMgmt = BlendedModule("blended.demo.mgmt")
val blendedDemoMgmtResources = BlendedModule("blended.demo.mgmt.resources")
val blendedDemoNode = BlendedModule("blended.demo.node")
val blendedDemoNodeResources = BlendedModule("blended.demo.node.resources")
val blendedDockerReactor = BlendedModule("blended.docker.reactor")
val blendedDockerDemoNode = BlendedModule("blended.docker.demo.node")
val blendedDockerDemoMgmt = BlendedModule("blended.docker.demo.mgmt")
val blendedDomino = BlendedModule("blended.domino")
val blendedFile = BlendedModule("blended.file")
val blendedHawtioLogin = BlendedModule("blended.hawtio.login")
val blendedItestReactor = BlendedModule("blended.itest.reactor")
val blendedItestSupport = BlendedModule("blended.itestsupport")
val blendedItestNode = BlendedModule("blended.itest.node")
val blendedJettyBoot = BlendedModule("blended.jetty.boot")
val blendedJmsUtils = BlendedModule("blended.jms.utils")
val blendedJmsSampler = BlendedModule("blended.jms.sampler")
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
val blendedMgmtServiceJmx = BlendedModule("blended.mgmt.service.jmx")
val blendedMgmtUi = BlendedModule("blended.mgmt.ui")
val blendedPersistence = BlendedModule("blended.persistence")
val blendedPersistenceOrient = BlendedModule("blended.persistence.orient")
val blendedPrickle = BlendedModule("blended.prickle")
val blendedSamplesReactor = BlendedModule("blended.samples.reactor")
val blendedSamplesCamel = BlendedModule("blended.samples.camel")
val blendedSamplesJms = BlendedModule("blended.samples.jms")
val blendedSamplesSprayHelloworld = BlendedModule("blended.samples.spray.helloworld")
val blendedScep = BlendedModule("blended.scep")
val blendedSecurity = BlendedModule("blended.security")
val blendedSecurityAkkaHttp = BlendedModule("blended.security.akka.http")
val blendedSecurityBoot = BlendedModule("blended.security.boot")
val blendedSecuritySsl = BlendedModule("blended.security.ssl")
val blendedSecurityLogin = BlendedModule("blended.security.login")
val blendedSecurityLoginRest = BlendedModule("blended.security.login.rest")
val blendedSecuritySpray = BlendedModule("blended.security.spray")
val blendedSpray = BlendedModule("blended.spray")
val blendedSprayApi = BlendedModule("blended.spray.api")
val blendedSslContext = BlendedModule("blended.sslcontext")
val blendedTestSupport = BlendedModule("blended.testsupport")
val blendedUpdater = BlendedModule("blended.updater")
val blendedUpdaterConfig = BlendedModule("blended.updater.config")
val blendedUpdaterMavenPlugin = BlendedModule("blended-updater-maven-plugin")
val blendedUpdaterRemote = BlendedModule("blended.updater.remote")
val blendedUpdaterTools = BlendedModule("blended.updater.tools")
val blendedUtil = BlendedModule("blended.util")
