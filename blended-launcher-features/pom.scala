import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  blendedLauncherFeatures,
  packaging = "jar",
  prerequisites = Prerequisites(
    maven = "3.3.3"
  ),
  // FIXME: Remove parent
  parent = blendedParent,
  dependencies = Seq(
    // tooling
    blendedUpdaterMavenPlugin % "provided",
    // blended-base
    felixFramework,
    blendedSecurityBoot,
    felixConfigAdmin,
    felixEventAdmin,
    felixFileinstall,
    felixGogoRuntime,
    felixGogoShell,
    felixGogoCommand,
    felixMetatype,
    typesafeConfig,
    blendedUpdater,
    blendedUpdaterConfig,
    blendedUtil,
    blendedContainerContext,
    blendedAkka,
    jclOverSlf4j,
    julToSlf4j,
    "org.ow2.asm" % "asm-all" % "4.1",
    logbackCore,
    logbackClassic,
    "com.typesafe.akka" % "akka-actor_${scala.version}" % "${akka.version}",
    "com.typesafe.akka" % "akka-osgi_${scala.version}" % "${akka.version}",
    "com.typesafe.akka" % "akka-slf4j_${scala.version}" % "${akka.version}",
    scalaLib,
    scalaReflect,
    scalaXml,
    // blended-activemq
    blendedActivemqBrokerstarter,
    "org.apache.servicemix.specs" % "org.apache.servicemix.specs.jaxb-api-2.2" % "2.5.0",
    "org.apache.servicemix.specs" % "org.apache.servicemix.specs.stax-api-1.0" % "2.4.0",
    "org.apache.aries.proxy" % "org.apache.aries.proxy.api" % "${aries.proxy.version}",
    "org.apache.aries.blueprint" % "org.apache.aries.blueprint.api" % "1.0.1",
    "org.apache.aries.blueprint" % "org.apache.aries.blueprint.core" % "1.4.3",
    "org.apache.geronimo.specs" % "geronimo-annotation_1.0_spec" % "1.1.1",
    "org.jvnet.jaxb2_commons" % "jaxb2-basics-runtime" % "0.6.4",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.jaxb-impl" % "2.2.1.1_2",
    Dependency(
      "org.apache.activemq" % "activemq-osgi" % "${activemq.version}",
      exclusions = Seq(
        "javax.jms" % "jms",
        "com.sun.jdmk" % "jmxtools",
        "com.sun.jmx" % "jmxri"
      )
    ),
    "de.wayofquality.blended" % "blended.activemq.client" % "${blended.version}",
    // blended-camel
    "org.apache.camel" % "camel-core" % "${camel.version}",
    "org.apache.camel" % "camel-spring" % "${camel.version}",
    "org.apache.camel" % "camel-jms" % "${camel.version}",
    "org.apache.camel" % "camel-http" % "${camel.version}",
    "org.apache.camel" % "camel-http-common" % "${camel.version}",
    "org.apache.camel" % "camel-servlet" % "${camel.version}",
    "org.apache.camel" % "camel-servletlistener" % "${camel.version}",
    blendedCamelUtils,
    blendedJmsUtils,
    // blended-commons
    "${project.groupId}" % "blended.jmx" % "${blended.version}",
    "org.apache.aries.jmx" % "org.apache.aries.jmx.api" % "${aries.jmx.version}",
    "org.apache.aries.jmx" % "org.apache.aries.jmx.core" % "${aries.jmx.version}",
    "org.apache.aries" % "org.apache.aries.util" % "${aries.util.version}",
    "org.apache.commons" % "com.springsource.org.apache.commons.collections" % "3.2.1",
    "org.apache.commons" % "com.springsource.org.apache.commons.discovery" % "0.4.0",
    commonsLang,
    commonsPool,
    commonsNet,
    commonsExec,
    "org.apache.commons" % "com.springsource.org.apache.commons.io" % "1.4.0",
    "org.apache.commons" % "com.springsource.org.apache.commons.codec" % "1.6.0",
    "org.apache.commons" % "com.springsource.org.apache.commons.httpclient" % "3.1.0",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.commons-beanutils" % "1.8.3_2",
    // blended-samples
    "${project.groupId}" % "blended.activemq.defaultbroker" % "${blended.version}",
    "${project.groupId}.samples" % "blended.samples.spray.helloworld" % "${blended.version}",
    "${project.groupId}.samples" % "blended.samples.camel" % "${blended.version}",
    "de.wayofquality.blended.samples" % "blended.samples.jms" % "${blended.version}",
    // blended-spray
    "${project.groupId}" % "blended.spray.api" % "${blended.version}",
    "${project.groupId}" % "blended.spray" % "${blended.version}",
    // blended-hawtio
    Dependency(
      "io.hawt" % "hawtio-web" % "${hawtio.version}",
      `type` = "war"
    ),
    "de.wayofquality.blended" % "blended.hawtio.login" % "${blended.version}",
    // http
    "org.ops4j.base" % "ops4j-base-lang" % "${ops4j-base.version}",
    "org.ops4j.pax.swissbox" % "pax-swissbox-core" % "${pax-swissbox.version}",
    "org.ops4j.pax.swissbox" % "pax-swissbox-optional-jcl" % "${pax-swissbox.version}",
    "org.ops4j.pax.web" % "pax-web-api" % "${pax-web.version}",
    "org.ops4j.pax.web" % "pax-web-spi" % "${pax-web.version}",
    "org.ops4j.pax.web" % "pax-web-runtime" % "${pax-web.version}",
    "org.ops4j.pax.web" % "pax-web-jetty" % "${pax-web.version}",
    "org.ops4j.pax.web" % "pax-web-jsp" % "${pax-web.version}",
    "org.ops4j.pax.web" % "pax-web-extender-whiteboard" % "${pax-web.version}",
    "org.ops4j.pax.web" % "pax-web-extender-war" % "${pax-web.version}",
    "org.apache.xbean" % "xbean-bundleutils" % "${xbean.version}",
    "org.apache.xbean" % "xbean-asm4-shaded" % "${xbean.asm4.version}",
    "org.apache.xbean" % "xbean-reflect" % "${xbean.version}",
    "org.apache.xbean" % "xbean-spring" % "${xbean.version}",
    "org.apache.xbean" % "xbean-finder-shaded" % "${xbean.finder.version}",
    // blended-jetty
    "org.apache.servicemix.specs" % "org.apache.servicemix.specs.activation-api-1.1" % "2.2.0",
    "javax.mail" % "mail" % "${javax.mail.version}",
    "org.apache.geronimo.specs" % "geronimo-annotation_1.1_spec" % "1.0.1",
    "org.apache.geronimo.specs" % "geronimo-jaspic_1.0_spec" % "1.1",
    "org.eclipse.jetty.aggregate" % "jetty-all-server" % "${jetty.version}",
    // blended-spring
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.aopalliance" % "1.0_6",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-core" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-expression" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-beans" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-aop" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-context" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-context-support" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-tx" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-jms" % "${spring.version}_1",
    // blended-jaxrs
    "org.codehaus.jettison" % "jettison" % "${jettison.version}",
    "org.codehaus.jackson" % "jackson-core-asl" % "${jackson.version}",
    "org.codehaus.jackson" % "jackson-mapper-asl" % "${jackson.version}",
    "org.codehaus.jackson" % "jackson-jaxrs" % "${jackson.version}",
    "com.sun.jersey" % "jersey-core" % "${jersey.version}",
    "com.sun.jersey" % "jersey-json" % "${jersey.version}",
    "com.sun.jersey" % "jersey-server" % "${jersey.version}",
    "com.sun.jersey" % "jersey-servlet" % "${jersey.version}",
    "com.sun.jersey" % "jersey-client" % "${jersey.version}",
    // blended-management
    blendedMgmtAgent,
    blendedContainerRegistry,
    blendedMgmtRest,
    blendedPersistence,
    blendedPersistenceOrient,
    "com.orientechnologies" % "orientdb-core" % "2.2.0",
    "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.1",
    "com.google.code.findbugs" % "jsr305" % "3.0.1",
    Dependency(
      blendedMgmtUi,
      `type` = "war"
    ),
    // blended-security
    "org.apache.shiro" % "shiro-core" % "${shiro.version}",
    "org.apache.shiro" % "shiro-web" % "${shiro.version}",
    blendedSecurity
  ),
  plugins = Seq(
      Plugin(
        blendedUpdaterMavenPlugin,
        executions = Seq(
          Execution(
            id = "make-features",
            phase = "compile",
            goals = Seq(
              "build-features"
            ),
            configuration = Config(
              srcFeatureDir = "${project.build.directory}/classes",
              resolveFromDependencies = "true"
            )
          )
        )
      )
  )
)
