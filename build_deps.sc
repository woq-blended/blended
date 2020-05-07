import mill.scalalib._

trait Deps { deps =>

  // Versions
  def activeMqVersion = "5.15.6"
  def akkaVersion = "2.5.26"
  def akkaHttpVersion = "10.1.11"
  def camelVersion = "2.19.5"
  def dominoVersion = "1.1.5"
  def jettyVersion = "9.4.28.v20200408"
  def jolokiaVersion = "1.6.2"
  def microJsonVersion = "1.6"
  def parboiledVersion = "1.1.6"
  def prickleVersion = "1.1.14"
  def scalaJsVersion = "0.6.32"
  def scalaVersion = "2.12.11"
  def scalaBinVersion(scalaVersion: String) = scalaVersion.split("[.]").take(2).mkString(".")
  def scalatestVersion = "3.1.1"
  def scalaCheckVersion = "1.14.3"
  def scoverageVersion = "1.4.1"
  def slf4jVersion = "1.7.25"
  def sprayVersion = "1.3.4"
  def springVersion = "4.3.12.RELEASE_1"

  def activeMqBroker = ivy"org.apache.activemq:activemq-broker:${activeMqVersion}"
  def activeMqClient = ivy"org.apache.activemq:activemq-client:${activeMqVersion}"
  def activeMqKahadbStore = ivy"org.apache.activemq:activemq-kahadb-store:${activeMqVersion}"
  def activeMqSpring = ivy"org.apache.activemq:activemq-spring:${activeMqVersion}"

  protected def akka(m: String) = ivy"com.typesafe.akka::akka-${m}:${akkaVersion}"

  protected def akkaHttpModule(m: String) = ivy"com.typesafe.akka::akka-${m}:${akkaHttpVersion}"

  def akkaActor = akka("actor")
  def akkaCamel = akka("camel")
  def akkaHttp = akkaHttpModule("http")
  def akkaHttpCore = akkaHttpModule("http-core")
  def akkaHttpTestkit = akkaHttpModule("http-testkit")
  def akkaOsgi = akka("osgi")
  def akkaParsing = akkaHttpModule("parsing")
  def akkaPersistence = akka("persistence")
  def akkaStream = akka("stream")
  def akkaStreamTestkit = akka("stream-testkit")
  def akkaTestkit = akka("testkit")
  def akkaSlf4j = akka("slf4j")

  def asciiRender = ivy"com.indvd00m.ascii.render:ascii-render:1.2.3"

  def bouncyCastleBcprov = ivy"org.bouncycastle:bcprov-jdk15on:1.60"
  def bouncyCastlePkix = ivy"org.bouncycastle:bcpkix-jdk15on:1.60"

  def cmdOption = ivy"de.tototec:de.tototec.cmdoption:0.6.0"
  def commonsBeanUtils = ivy"commons-beanutils:commons-beanutils:1.9.3"
  def commonsCodec = ivy"commons-codec:commons-codec:1.11"
  def commonsCompress = ivy"org.apache.commons:commons-compress:1.13"
  def commonsDaemon = ivy"commons-daemon:commons-daemon:1.0.15"
  def commonsIo = ivy"commons-io:commons-io:2.6"
  def commonsLang2 = ivy"commons-lang:commons-lang:2.6"
  def concurrentLinkedHashMapLru = ivy"com.googlecode.concurrentlinkedhashmap:concurrentlinkedhashmap-lru:1.4.2"

  def dockerJava = ivy"com.github.docker-java:docker-java:3.0.13"
  def domino = ivy"com.github.domino-osgi::domino:${dominoVersion}"

  def felixConnect = ivy"org.apache.felix:org.apache.felix.connect:0.1.0"
  def felixGogoCommand = ivy"org.apache.felix:org.apache.felix.gogo.command:1.1.0"
  def felixGogoJline = ivy"org.apache.felix:org.apache.felix.gogo.jline:1.1.4"
  def felixGogoShell = ivy"org.apache.felix:org.apache.felix.gogo.shell:1.1.2"
  def felixGogoRuntime = ivy"org.apache.felix:org.apache.felix.gogo.runtime:1.1.2"
  def felixFileinstall = ivy"org.apache.felix:org.apache.felix.fileinstall:3.4.2"
  def felixFramework = ivy"org.apache.felix:org.apache.felix.framework:6.0.2"

  def geronimoJms11Spec = ivy"org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1"

  def h2 = ivy"com.h2database:h2:1.4.197"
  def hikaricp = ivy"com.zaxxer:HikariCP:3.1.0"

  protected def jettyOsgi(n: String) = ivy"org.eclipse.jetty.osgi:jetty-${n}:${jettyVersion}"

  def jaxb = ivy"org.glassfish.jaxb:jaxb-runtime:2.3.1"
  def jcip = ivy"net.jcip:jcip-annotations:1.0"
  def jclOverSlf4j = ivy"org.slf4j:jcl-over-slf4j:${slf4jVersion}"
  def jettyOsgiBoot = jettyOsgi("osgi-boot")
  def jjwt = ivy"io.jsonwebtoken:jjwt:0.7.0"
  def jms11Spec = ivy"org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1"
  def jolokiaJvm = ivy"org.jolokia:jolokia-jvm:${jolokiaVersion}"
  def jolokiaJvmAgent = ivy"org.jolokia:jolokia-jvm:${jolokiaVersion};classifier=agent"
  def jscep = ivy"com.google.code.jscep:jscep:2.5.0"
  def jsonLenses = ivy"net.virtual-void::json-lenses:0.6.2"
  def julToSlf4j = ivy"org.slf4j:jul-to-slf4j:${slf4jVersion}"
  def junit = ivy"junit:junit:4.12"

  def lambdaTest = ivy"de.tototec:de.tobiasroeser.lambdatest:0.6.2"
  def levelDbJava = ivy"org.iq80.leveldb:leveldb:0.9"
  def levelDbJni = ivy"org.fusesource.leveldbjni:leveldbjni-all:1.8"
  def liquibase = ivy"org.liquibase:liquibase-core:3.6.1"
  def logbackCore = ivy"ch.qos.logback:logback-core:1.2.3"
  def logbackClassic = ivy"ch.qos.logback:logback-classic:1.2.3"

  def microjson = ivy"com.github.benhutchison::microjson:${microJsonVersion}"
  def mimepull = ivy"org.jvnet.mimepull:mimepull:1.9.5"
  def mockitoAll = ivy"org.mockito:mockito-all:1.9.5"

  def orgOsgi = ivy"org.osgi:org.osgi.core:6.0.0"
  def orgOsgiCompendium = ivy"org.osgi:org.osgi.compendium:5.0.0"
  def osLib = ivy"com.lihaoyi::os-lib:0.4.2"

  def parboiledCore = ivy"org.parboiled:parboiled-core:${parboiledVersion}"
  def parboiledScala = ivy"org.parboiled::parboiled-scala:${parboiledVersion}"
  def prickle = ivy"com.github.benhutchison::prickle:${prickleVersion}"

  // SCALA
  def scalaLibrary(scalaVersion: String) = ivy"org.scala-lang:scala-library:${scalaVersion}"
  def scalaReflect(scalaVersion: String) = ivy"org.scala-lang:scala-reflect:${scalaVersion}"
  def scalaParser = ivy"org.scala-lang.modules::scala-parser-combinators:1.1.1"
  def scalaXml = ivy"org.scala-lang.modules::scala-xml:1.1.0"

  def scalacheck = ivy"org.scalacheck::scalacheck:1.14.0"
  def scalatest = ivy"org.scalatest::scalatest:${scalatestVersion}"
  def scalatestplusScalacheck = ivy"org.scalatestplus::scalacheck-1-14:3.1.1.1"
  def shapeless = ivy"com.chuusai::shapeless:1.2.4"
  def slf4j = ivy"org.slf4j:slf4j-api:${slf4jVersion}"
  def slf4jLog4j12 = ivy"org.slf4j:slf4j-log4j12:${slf4jVersion}"
  def snakeyaml = ivy"org.yaml:snakeyaml:1.18"
  def sprayJson = ivy"io.spray::spray-json:${sprayVersion}"

  //  protected def spring(n: String) = ivy"org.springframework" % s"spring-${n}" % springVersion
  protected def spring(n: String) = ivy"org.apache.servicemix.bundles:org.apache.servicemix.bundles.spring-${n}:${springVersion}"

  def springBeans = spring("beans")
  def springAop = spring("aop")
  def springContext = spring("context")
  def springContextSupport = spring("context-support")
  def springExpression = spring("expression")
  def springCore = spring("core")
  def springJdbc = spring("jdbc")
  def springJms = spring("jms")
  def springTx = spring("tx")

  def sttp = ivy"com.softwaremill.sttp.client::core:2.0.6"
  def sttpAkka = ivy"com.softwaremill.sttp.client::akka-http-backend:2.0.6"

  def typesafeConfig = ivy"com.typesafe:config:1.3.3"
  def typesafeSslConfigCore = ivy"com.typesafe::ssl-config-core:0.3.6"

  // libs for splunk support via HEC
  def splunkjava = ivy"com.splunk.logging:splunk-library-jadefogging:1.7.3"
  def httpCore = ivy"org.apache.httpcomponents:httpcore:4.4.9"
  def httpCoreNio = ivy"org.apache.httpcomponents:httpcore:4.4.6"
  def httpComponents = ivy"org.apache.httpcomponents:httpclient:4.5.5"
  def httpAsync = ivy"org.apache.httpcomponents:httpasyncclient:4.1.3"
  def commonsLogging = ivy"commons-logging:commons-logging:1.2"
  def jsonSimple = ivy"com.googlecode.json-simple:json-simple:1.1.1"

  object js {
    def prickle = ivy"com.github.benhutchison::prickle::${prickleVersion}"
    def scalatest = ivy"org.scalatest::scalatest::${scalatestVersion}"
    def scalacheck = ivy"org.scalacheck::scalacheck::${scalaCheckVersion}"
    def scalatestplusScalacheck = ivy"org.scalatestplus::scalacheck-1-14::3.1.1.1"
  }

}

object Deps {
  def scalaVersions: Map[String, Deps] = Seq(Deps_2_12, Deps_2_13).map(d => d.scalaVersion -> d).toMap

  object Deps_2_12 extends Deps
  object Deps_2_13 extends Deps {
    override def scalaVersion = "2.13.2"
    override def scalaJsVersion = "1.0.1"
    override def prickleVersion = "1.1.16"
    override def microJsonVersion = "1.4"
  }
}
