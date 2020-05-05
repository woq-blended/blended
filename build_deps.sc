import mill.scalalib._

trait Deps { deps =>

  // Versions
  val activeMqVersion = "5.15.6"
  val akkaVersion = "2.5.26"
  val akkaHttpVersion = "10.1.11"
  val camelVersion = "2.19.5"
  val dominoVersion = "1.1.4"
  val jettyVersion = "9.4.28.v20200408"
  val jolokiaVersion = "1.6.2"
  val microJsonVersion = "1.4"
  val parboiledVersion = "1.1.6"
  val prickleVersion = "1.1.14"
  def scalaJsVersion = "0.6.32"
  def scalaVersion = "2.12.11"
  def scalaBinVersion(scalaVersion: String) = scalaVersion.split("[.]").take(2).mkString(".")
  val scalatestVersion = "3.0.8"
  val scalaCheckVersion = "1.14.0"
  val scoverageVersion = "1.4.1"
  val slf4jVersion = "1.7.25"
  val sprayVersion = "1.3.4"
  val springVersion = "4.3.12.RELEASE_1"

  val activeMqBroker = ivy"org.apache.activemq:activemq-broker:${activeMqVersion}"
  val activeMqClient = ivy"org.apache.activemq:activemq-client:${activeMqVersion}"
  val activeMqKahadbStore = ivy"org.apache.activemq:activemq-kahadb-store:${activeMqVersion}"
  val activeMqSpring = ivy"org.apache.activemq:activemq-spring:${activeMqVersion}"

  protected def akka(m: String) = ivy"com.typesafe.akka::akka-${m}:${akkaVersion}"

  protected def akkaHttpModule(m: String) = ivy"com.typesafe.akka::akka-${m}:${akkaHttpVersion}"

  val akkaActor = akka("actor")
  val akkaCamel = akka("camel")
  val akkaHttp = akkaHttpModule("http")
  val akkaHttpCore = akkaHttpModule("http-core")
  val akkaHttpTestkit = akkaHttpModule("http-testkit")
  val akkaOsgi = akka("osgi")
  val akkaParsing = akkaHttpModule("parsing")
  val akkaPersistence = akka("persistence")
  val akkaStream = akka("stream")
  val akkaStreamTestkit = akka("stream-testkit")
  val akkaTestkit = akka("testkit")
  val akkaSlf4j = akka("slf4j")

  val asciiRender = ivy"com.indvd00m.ascii.render:ascii-render:1.2.3"

  val bouncyCastleBcprov = ivy"org.bouncycastle:bcprov-jdk15on:1.60"
  val bouncyCastlePkix = ivy"org.bouncycastle:bcpkix-jdk15on:1.60"

  val cmdOption = ivy"de.tototec:de.tototec.cmdoption:0.6.0"
  val commonsBeanUtils = ivy"commons-beanutils:commons-beanutils:1.9.3"
  val commonsCodec = ivy"commons-codec:commons-codec:1.11"
  val commonsCompress = ivy"org.apache.commons:commons-compress:1.13"
  val commonsDaemon = ivy"commons-daemon:commons-daemon:1.0.15"
  val commonsIo = ivy"commons-io:commons-io:2.6"
  val commonsLang2 = ivy"commons-lang:commons-lang:2.6"
  val concurrentLinkedHashMapLru = ivy"com.googlecode.concurrentlinkedhashmap:concurrentlinkedhashmap-lru:1.4.2"

  val dockerJava = ivy"com.github.docker-java:docker-java:3.0.13"
  val domino = ivy"com.github.domino-osgi::domino:${dominoVersion}"

  val felixConnect = ivy"org.apache.felix:org.apache.felix.connect:0.1.0"
  val felixGogoCommand = ivy"org.apache.felix:org.apache.felix.gogo.command:1.1.0"
  val felixGogoJline = ivy"org.apache.felix:org.apache.felix.gogo.jline:1.1.4"
  val felixGogoShell = ivy"org.apache.felix:org.apache.felix.gogo.shell:1.1.2"
  val felixGogoRuntime = ivy"org.apache.felix:org.apache.felix.gogo.runtime:1.1.2"
  val felixFileinstall = ivy"org.apache.felix:org.apache.felix.fileinstall:3.4.2"
  val felixFramework = ivy"org.apache.felix:org.apache.felix.framework:6.0.2"

  val geronimoJms11Spec = ivy"org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1"

  val h2 = ivy"com.h2database:h2:1.4.197"
  val hikaricp = ivy"com.zaxxer:HikariCP:3.1.0"

  protected def jettyOsgi(n: String) = ivy"org.eclipse.jetty.osgi:jetty-${n}:${jettyVersion}"

  val jaxb = ivy"org.glassfish.jaxb:jaxb-runtime:2.3.1"
  val jcip = ivy"net.jcip:jcip-annotations:1.0"
  val jclOverSlf4j = ivy"org.slf4j:jcl-over-slf4j:${slf4jVersion}"
  val jettyOsgiBoot = jettyOsgi("osgi-boot")
  val jjwt = ivy"io.jsonwebtoken:jjwt:0.7.0"
  val jms11Spec = ivy"org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1"
  val jolokiaJvm = ivy"org.jolokia:jolokia-jvm:${jolokiaVersion}"
  val jolokiaJvmAgent = ivy"org.jolokia:jolokia-jvm:${jolokiaVersion};classifier=agent"
  val jscep = ivy"com.google.code.jscep:jscep:2.5.0"
  val jsonLenses = ivy"net.virtual-void::json-lenses:0.6.2"
  val julToSlf4j = ivy"org.slf4j:jul-to-slf4j:${slf4jVersion}"
  val junit = ivy"junit:junit:4.12"

  val lambdaTest = ivy"de.tototec:de.tobiasroeser.lambdatest:0.6.2"
  val levelDbJava = ivy"org.iq80.leveldb:leveldb:0.9"
  val levelDbJni = ivy"org.fusesource.leveldbjni:leveldbjni-all:1.8"
  val liquibase = ivy"org.liquibase:liquibase-core:3.6.1"
  /** Only for use in test that also runs in JS */
  val log4s = ivy"org.log4s::log4s:1.6.1"
  val logbackCore = ivy"ch.qos.logback:logback-core:1.2.3"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.2.3"

  val microjson = ivy"com.github.benhutchison::microjson:${microJsonVersion}"
  val mimepull = ivy"org.jvnet.mimepull:mimepull:1.9.5"
  val mockitoAll = ivy"org.mockito:mockito-all:1.9.5"

  val orgOsgi = ivy"org.osgi:org.osgi.core:6.0.0"
  val orgOsgiCompendium = ivy"org.osgi:org.osgi.compendium:5.0.0"
  val osLib = ivy"com.lihaoyi::os-lib:0.4.2"

  val parboiledCore = ivy"org.parboiled:parboiled-core:${parboiledVersion}"
  val parboiledScala = ivy"org.parboiled::parboiled-scala:${parboiledVersion}"
  val prickle = ivy"com.github.benhutchison::prickle:${prickleVersion}"

  // SCALA
  def scalaLibrary(scalaVersion: String) = ivy"org.scala-lang:scala-library:${scalaVersion}"
  def scalaReflect(scalaVersion: String) = ivy"org.scala-lang:scala-reflect:${scalaVersion}"
  val scalaParser = ivy"org.scala-lang.modules::scala-parser-combinators:1.1.1"
  val scalaXml = ivy"org.scala-lang.modules::scala-xml:1.1.0"

  val scalacheck = ivy"org.scalacheck::scalacheck:1.14.0"
  val scalatest = ivy"org.scalatest::scalatest:${scalatestVersion}"
  val shapeless = ivy"com.chuusai::shapeless:1.2.4"
  val slf4j = ivy"org.slf4j:slf4j-api:${slf4jVersion}"
  val slf4jLog4j12 = ivy"org.slf4j:slf4j-log4j12:${slf4jVersion}"
  val snakeyaml = ivy"org.yaml:snakeyaml:1.18"
  val sprayJson = ivy"io.spray::spray-json:${sprayVersion}"

  //  protected def spring(n: String) = ivy"org.springframework" % s"spring-${n}" % springVersion
  protected def spring(n: String) = ivy"org.apache.servicemix.bundles:org.apache.servicemix.bundles.spring-${n}:${springVersion}"

  val springBeans = spring("beans")
  val springAop = spring("aop")
  val springContext = spring("context")
  val springContextSupport = spring("context-support")
  val springExpression = spring("expression")
  val springCore = spring("core")
  val springJdbc = spring("jdbc")
  val springJms = spring("jms")
  val springTx = spring("tx")

  val sttp = ivy"com.softwaremill.sttp.client::core:2.0.6"
  val sttpAkka = ivy"com.softwaremill.sttp.client::akka-http-backend:2.0.6"

  val typesafeConfig = ivy"com.typesafe:config:1.3.3"
  val typesafeSslConfigCore = ivy"com.typesafe::ssl-config-core:0.3.6"

  // libs for splunk support via HEC
  val splunkjava = ivy"com.splunk.logging:splunk-library-javalogging:1.7.3"
  val httpCore = ivy"org.apache.httpcomponents:httpcore:4.4.9"
  val httpCoreNio = ivy"org.apache.httpcomponents:httpcore:4.4.6"
  val httpComponents = ivy"org.apache.httpcomponents:httpclient:4.5.5"
  val httpAsync = ivy"org.apache.httpcomponents:httpasyncclient:4.1.3"
  val commonsLogging = ivy"commons-logging:commons-logging:1.2"
  val jsonSimple = ivy"com.googlecode.json-simple:json-simple:1.1.1"

  object js {
    val log4s = ivy"org.log4s::log4s::${deps.log4s.dep.version}"
    val prickle = ivy"com.github.benhutchison::prickle::${prickleVersion}"
    val scalatest = ivy"org.scalatest::scalatest::${scalatestVersion}"
    val scalacheck = ivy"org.scalacheck::scalacheck::${scalaCheckVersion}"
  }

}

object Deps {
  val scalaVersions: Map[String, Deps] = Seq(Deps_2_12, Deps_2_13).map(d => d.scalaVersion -> d).toMap

  object Deps_2_12 extends Deps
  object Deps_2_13 extends Deps {
    override def scalaVersion: String = "2.13.2"
  }
}
