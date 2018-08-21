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

// Dependencies
object Deps {
  val activationApi = "org.apache.servicemix.specs" % "org.apache.servicemix.specs.activation-api-1.1" % "2.2.0"

  val activeMqBroker = "org.apache.activemq" % "activemq-broker" % BlendedVersions.activeMqVersion
  val activeMqClient = "org.apache.activemq" % "activemq-client" % BlendedVersions.activeMqVersion
  val activeMqSpring = "org.apache.activemq" % "activemq-spring" % BlendedVersions.activeMqVersion
  val activeMqOsgi = "org.apache.activemq" % "activemq-osgi" % BlendedVersions.activeMqVersion
  val activeMqKahadbStore = "org.apache.activemq" % "activemq-kahadb-store" % BlendedVersions.activeMqVersion

  def akka(m: String) = "com.typesafe.akka" %% s"akka-${m}" % BlendedVersions.akkaVersion
  def akka_Http(m: String) = "com.typesafe.akka" %% s"akka-${m}" % BlendedVersions.akkaHttpVersion
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

  val commonsBeanUtils = "commons-beanutils" % "commons-beanutils" % "1.9.3"
  val commonsCodec = "commons-codec" % "commons-codec" % "1.11"
  val commonsCompress = "org.apache.commons" % "commons-compress" % "1.13"
  val commonsConfiguration2 = "org.apache.commons" % "commons-configuration2" % "2.2"
  val commonsCollections = "org.apache.commons" % "com.springsource.org.apache.commons.collections" % "3.2.1"
  val commonsDaemon = "commons-daemon" % "commons-daemon" % "1.0.15"
  val commonsDiscovery = "org.apache.commons" % "com.springsource.org.apache.commons.discovery" % "0.4.0"
  val commonsExec = "org.apache.commons" % "commons-exec" % "1.3"
  val commonsHttpclient = "org.apache.commons" % "com.springsource.org.apache.commons.httpclient" % "3.1.0"
  val commonsIo = "commons-io" % "commons-io" % "2.6"
  val commonsLang3 = "org.apache.commons" % "commons-lang3" % "3.7"
  val commonsLang2 = "commons-lang" % "commons-lang" % "2.6"
  val commonsLang = commonsLang3
  val commonsNet = "commons-net" % "commons-net" % "3.3"
  val commonsPool = "commons-pool" % "commons-pool" % "1.6"

  val cmdOption = "de.tototec" % "de.tototec.cmdoption" % "0.6.0"
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

  val h2 = "com.h2database" % "h2" % "1.4.197"
  val hikaricp = "com.zaxxer" % "HikariCP" % "3.1.0"
  val hawtioWeb = Dependency(gav = "io.hawt" % "hawtio-web" % "1.5.8", `type` = "war")

  val javaxEl = "javax.el" % "javax.el-api" % "3.0.1-b04"
  val javaxMail = "javax.mail" % "mail" % "1.4.5"
  val javaxServlet31 = "org.everit.osgi.bundles" % "org.everit.osgi.bundles.javax.servlet.api" % "3.1.0"

  val jacksonCore = "com.fasterxml.jackson.core" % "jackson-core" % "2.9.3"
  val jacksonBind = "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.3"
  val jacksonAnnotations = "com.fasterxml.jackson.core" % "jackson-annotations" % "2.9.3"

  val jcip = "net.jcip" % "jcip-annotations" % "1.0"
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
  val jsonLenses = "net.virtual-void" %% "json-lenses" % "0.6.2"
  val jolokiaJvm = "org.jolokia" % "jolokia-jvm" % BlendedVersions.jolokiaVersion
  val jolokiaJvmAgent = Dependency(
    jolokiaJvm,
    classifier = "agent"
  )
  val juliOverSlf4j = "com.github.akiraly.reusable-poms" % "tomcat-juli-over-slf4j" % "4"
  val junit = "junit" % "junit" % "4.11"
  val julToSlf4j = "org.slf4j" % "jul-to-slf4j" % BlendedVersions.slf4jVersion
  val jscep = "com.google.code.jscep" % "jscep" % "2.5.0"
  val jsr305 = "com.google.code.findbugs" % "jsr305" % "3.0.1"

  val lambdaTest = "de.tototec" % "de.tobiasroeser.lambdatest" % "0.6.2"
  val liquibase = "org.liquibase" % "liquibase-core" % "3.6.1"
  val logbackCore = "ch.qos.logback" % "logback-core" % "1.2.3"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"

  val mockitoAll = "org.mockito" % "mockito-all" % "1.9.5"
  val microjson = "com.github.benhutchison" %% "microjson" % "1.4"

  val ops4jBaseLang = "org.ops4j.base" % "ops4j-base-lang" % "1.4.0"

  val orgOsgi = "org.osgi" % "org.osgi.core" % "6.0.0"
  val orgOsgiCompendium = "org.osgi" % "org.osgi.compendium" % "5.0.0"

  val paxSwissboxCore = "org.ops4j.pax.swissbox" % "pax-swissbox-core" % "1.7.0"
  val paxSwissboxOptJcl = "org.ops4j.pax.swissbox" % "pax-swissbox-optional-jcl" % "1.7.0"
  val prickle = "com.github.benhutchison" %% "prickle" % BlendedVersions.prickle

  val reactiveStreams = "org.reactivestreams" % "reactive-streams" % "1.0.0.final"

  val scalaCompatJava8 = "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"
  val scalaLib = "org.scala-lang" % "scala-library" % BlendedVersions.scalaVersion
  val scalaParser = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.6"
  val scalaReflect = "org.scala-lang" % "scala-reflect" % BlendedVersions.scalaVersion
  val scalaTest = "org.scalatest" %% "scalatest" % BlendedVersions.scalaTestVersion
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.6"

  val servicemixJaxbApi = "org.apache.servicemix.specs" % "org.apache.servicemix.specs.jaxb-api-2.2" % "2.5.0"
  val servicemixJaxbImpl = "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.jaxb-impl" % "2.2.1.1_2"
  val servicemixJaxbRuntime = "org.jvnet.jaxb2_commons" % "jaxb2-basics-runtime" % "0.6.4"
  val servicemixStaxApi = "org.apache.servicemix.specs" % "org.apache.servicemix.specs.stax-api-1.0" % "2.4.0"

  val slf4j = "org.slf4j" % "slf4j-api" % BlendedVersions.slf4jVersion
  val slf4jJcl = "org.slf4j" % "jcl-over-slf4j" % BlendedVersions.slf4jVersion
  val slf4jJul = "org.slf4j" % "jul-to-slf4j" % BlendedVersions.slf4jVersion
  val slf4jLog4j12 = "org.slf4j" % "slf4j-log4j12" % BlendedVersions.slf4jVersion

  val snakeyaml = "org.yaml" % "snakeyaml" % "1.18"
  
  val sprayJson = "io.spray" %% s"spray-json" % BlendedVersions.sprayVersion

  private def spring(n: String) = "org.apache.servicemix.bundles" % s"org.apache.servicemix.bundles.spring-${n}" % BlendedVersions.springVersion
  val springBeans = spring("beans")
  val springAop = spring("aop")
  val springContext = spring("context")
  val springContextSupport = spring("context-support")
  val springExpression = spring("expression")
  val springCore = spring("core")
  val springJdbc = spring("jdbc")
  val springJms = spring("jms")
  val springTx = spring("tx")

  val sttp = "com.softwaremill.sttp" %% "core" % "1.3.0"
  val sttpAkka = "com.softwaremill.sttp" %% "akka-http-backend" % "1.3.0"

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

object Blended {
  def blended(name: String) = BlendedVersions.blendedGroupId % name % BlendedVersions.blendedVersion

  val activemqBrokerstarter = blended("blended.activemq.brokerstarter")
  val activemqClient = blended("blended.activemq.client")
  val activemqDefaultbroker = blended("blended.activemq.defaultbroker")
  val akka = blended("blended.akka")
  val akkaHttp = blended("blended.akka.http")
  val akkaHttpApi = blended("blended.akka.http.api")
  val akkaHttpJmsQueue = blended("blended.akka.http.jmsqueue")
  val akkaHttpProxy = blended("blended.akka.http.proxy")
  val akkaHttpRestJms = blended("blended.akka.http.restjms")
  val akkaHttpSampleHelloworld = blended("blended.akka.http.sample.helloworld")
  val camelUtils = blended("blended.camel.utils")
  val containerContextApi = blended("blended.container.context.api")
  val containerContextImpl = blended("blended.container.context.impl")
  val containerRegistry = blended("blended.container.registry")
  val demoReactor = blended("blended.demo.reactor")
  val demoMgmt = blended("blended.demo.mgmt")
  val demoMgmtResources = blended("blended.demo.mgmt.resources")
  val demoNode = blended("blended.demo.node")
  val demoNodeResources = blended("blended.demo.node.resources")
  val dockerDemoApacheDS = blended("blended.docker.demo.apacheds")
  val dockerReactor = blended("blended.docker.reactor")
  val dockerDemoNode = blended("blended.docker.demo.node")
  val dockerDemoMgmt = blended("blended.docker.demo.mgmt")
  val domino = blended("blended.domino")
  val file = blended("blended.file")
  val hawtioLogin = blended("blended.hawtio.login")
  val itestReactor = blended("blended.itest.reactor")
  val itestSupport = blended("blended.itestsupport")
  val itestNode = blended("blended.itest.node")
  val jettyBoot = blended("blended.jetty.boot")
  val jmsUtils = blended("blended.jms.utils")
  val jmsSampler = blended("blended.jms.sampler")
  val jmx = blended("blended.jmx")
  val jolokia = blended("blended.jolokia")
  val launcher = blended("blended.launcher")
  val launcherFeatures = blended("blended.launcher.features")
  val mgmtAgent = blended("blended.mgmt.agent")
  val mgmtBase = blended("blended.mgmt.base")
  val mgmtRepo = blended("blended.mgmt.repo")
  val mgmtRepoRest = blended("blended.mgmt.repo.rest")
  val mgmtMock = blended("blended.mgmt.mock")
  val mgmtRest = blended("blended.mgmt.rest")
  val mgmtServiceJmx = blended("blended.mgmt.service.jmx")
  val mgmtWs = blended("blended.mgmt.ws")
  val persistence = blended("blended.persistence")
  val persistenceH2 = blended("blended.persistence.h2")
  val prickle = blended("blended.prickle")
  val prickleAkkaHttp = blended("blended.prickle.akka.http")
  val samplesReactor = blended("blended.samples.reactor")
  val samplesCamel = blended("blended.samples.camel")
  val samplesJms = blended("blended.samples.jms")
  val security = blended("blended.security")
  val securityAkkaHttp = blended("blended.security.akka.http")
  val securityBoot = blended("blended.security.boot")
  val securityScep = blended("blended.security.scep")
  val securityScepStandalone = blended("blended.security.scep.standalone")
  val securitySsl = blended("blended.security.ssl")
  val securityLogin = blended("blended.security.login")
  val securityLoginRest = blended("blended.security.login.rest")
  val sslContext = blended("blended.sslcontext")
  val testSupport = blended("blended.testsupport")
  val testSupportPojosr = blended("blended.testsupport.pojosr")
  val updater = blended("blended.updater")
  val updaterConfig = blended("blended.updater.config")
  val updaterMavenPlugin = blended("blended-updater-maven-plugin")
  val updaterRemote = blended("blended.updater.remote")
  val updaterTools = blended("blended.updater.tools")
  val util = blended("blended.util")
  val utilLogging = blended("blended.util.logging")
}

