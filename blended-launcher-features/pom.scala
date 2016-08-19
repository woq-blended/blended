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
  parent = Parent(
    gav = "de.wayofquality.blended" % "blended.parent" % "2.0-SNAPSHOT",
    relativePath = "../blended-parent"
  ),
  dependencies = Seq(
    "de.wayofquality.blended" % "blended-updater-maven-plugin" % "${blended.version}" % "provided",
    "org.apache.felix" % "org.apache.felix.framework" % "5.0.0",
    "de.wayofquality.blended" % "blended.security.boot" % "${blended.version}",
    "org.apache.felix" % "org.apache.felix.configadmin" % "${felix.ca.version}",
    "org.apache.felix" % "org.apache.felix.eventadmin" % "${felix.event.version}",
    "org.apache.felix" % "org.apache.felix.fileinstall" % "${felix.fileinstall.version}",
    "org.apache.felix" % "org.apache.felix.gogo.runtime" % "${felix.gogo.runtime.version}",
    "org.apache.felix" % "org.apache.felix.gogo.shell" % "${felix.gogo.shell.version}",
    "org.apache.felix" % "org.apache.felix.gogo.command" % "${felix.gogo.command.version}",
    "org.apache.felix" % "org.apache.felix.metatype" % "${felix.metatype.version}",
    "com.typesafe" % "config" % "${typesafe.config.version}",
    "${project.groupId}" % "blended.updater" % "${blended.version}",
    "${project.groupId}" % "blended.updater.config" % "${blended.version}",
    "${project.groupId}" % "blended.util" % "${blended.version}",
    "${project.groupId}" % "blended.container.context" % "${blended.version}",
    "${project.groupId}" % "blended.akka" % "${blended.version}",
    "org.slf4j" % "jcl-over-slf4j" % "${slf4j.version}",
    "org.slf4j" % "jul-to-slf4j" % "${slf4j.version}",
    "org.ow2.asm" % "asm-all" % "4.1",
    "ch.qos.logback" % "logback-core" % "${logback.version}",
    "ch.qos.logback" % "logback-classic" % "${logback.version}",
    "com.typesafe.akka" % "akka-actor_${scala.version}" % "${akka.version}",
    "com.typesafe.akka" % "akka-osgi_${scala.version}" % "${akka.version}",
    "com.typesafe.akka" % "akka-slf4j_${scala.version}" % "${akka.version}",
    "org.scala-lang" % "scala-library" % "${scala.version}.${scala.micro.version}",
    "org.scala-lang" % "scala-reflect" % "${scala.version}.${scala.micro.version}",
    "org.scala-lang.modules" % "scala-xml_${scala.version}" % "${scala.xml.version}",
    "${project.groupId}" % "blended.activemq.brokerstarter" % "${blended.version}",
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
    "org.apache.camel" % "camel-core" % "${camel.version}",
    "org.apache.camel" % "camel-spring" % "${camel.version}",
    "org.apache.camel" % "camel-jms" % "${camel.version}",
    "org.apache.camel" % "camel-http" % "${camel.version}",
    "org.apache.camel" % "camel-http-common" % "${camel.version}",
    "org.apache.camel" % "camel-servlet" % "${camel.version}",
    "org.apache.camel" % "camel-servletlistener" % "${camel.version}",
    "${project.groupId}" % "blended.camel.utils" % "${blended.version}",
    "de.wayofquality.blended" % "blended.jms.utils" % "${blended.version}",
    "${project.groupId}" % "blended.jmx" % "${blended.version}",
    "org.apache.aries.jmx" % "org.apache.aries.jmx.api" % "${aries.jmx.version}",
    "org.apache.aries.jmx" % "org.apache.aries.jmx.core" % "${aries.jmx.version}",
    "org.apache.aries" % "org.apache.aries.util" % "${aries.util.version}",
    "org.apache.commons" % "com.springsource.org.apache.commons.collections" % "3.2.1",
    "org.apache.commons" % "com.springsource.org.apache.commons.discovery" % "0.4.0",
    "commons-lang" % "commons-lang" % "2.6",
    "commons-pool" % "commons-pool" % "1.6",
    "commons-net" % "commons-net" % "3.3",
    "org.apache.commons" % "commons-exec" % "1.3",
    "org.apache.commons" % "com.springsource.org.apache.commons.io" % "1.4.0",
    "org.apache.commons" % "com.springsource.org.apache.commons.codec" % "1.6.0",
    "org.apache.commons" % "com.springsource.org.apache.commons.httpclient" % "3.1.0",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.commons-beanutils" % "1.8.3_2",
    "${project.groupId}" % "blended.activemq.defaultbroker" % "${blended.version}",
    "${project.groupId}.samples" % "blended.samples.spray.helloworld" % "${blended.version}",
    "${project.groupId}.samples" % "blended.samples.camel" % "${blended.version}",
    "de.wayofquality.blended.samples" % "blended.samples.jms" % "${blended.version}",
    "${project.groupId}" % "blended.spray.api" % "${blended.version}",
    "${project.groupId}" % "blended.spray" % "${blended.version}",
    Dependency(
      "io.hawt" % "hawtio-web" % "${hawtio.version}",
      `type` = "war"
    ),
    "de.wayofquality.blended" % "blended.hawtio.login" % "${blended.version}",
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
    "org.apache.servicemix.specs" % "org.apache.servicemix.specs.activation-api-1.1" % "2.2.0",
    "javax.mail" % "mail" % "${javax.mail.version}",
    "org.apache.geronimo.specs" % "geronimo-annotation_1.1_spec" % "1.0.1",
    "org.apache.geronimo.specs" % "geronimo-jaspic_1.0_spec" % "1.1",
    "org.eclipse.jetty.aggregate" % "jetty-all-server" % "${jetty.version}",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.aopalliance" % "1.0_6",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-core" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-expression" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-beans" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-aop" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-context" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-context-support" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-tx" % "${spring.version}_1",
    "org.apache.servicemix.bundles" % "org.apache.servicemix.bundles.spring-jms" % "${spring.version}_1",
    "org.codehaus.jettison" % "jettison" % "${jettison.version}",
    "org.codehaus.jackson" % "jackson-core-asl" % "${jackson.version}",
    "org.codehaus.jackson" % "jackson-mapper-asl" % "${jackson.version}",
    "org.codehaus.jackson" % "jackson-jaxrs" % "${jackson.version}",
    "com.sun.jersey" % "jersey-core" % "${jersey.version}",
    "com.sun.jersey" % "jersey-json" % "${jersey.version}",
    "com.sun.jersey" % "jersey-server" % "${jersey.version}",
    "com.sun.jersey" % "jersey-servlet" % "${jersey.version}",
    "com.sun.jersey" % "jersey-client" % "${jersey.version}",
    "${project.groupId}" % "blended.mgmt.agent" % "${blended.version}",
    "${project.groupId}" % "blended.container.registry" % "${blended.version}",
    "${project.groupId}" % "blended.mgmt.rest" % "${blended.version}",
    "${project.groupId}" % "blended.persistence" % "${blended.version}",
    "${project.groupId}" % "blended.persistence.orient" % "${blended.version}",
    "com.orientechnologies" % "orientdb-core" % "2.2.0",
    "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.1",
    "com.google.code.findbugs" % "jsr305" % "3.0.1",
    Dependency(
      "${project.groupId}" % "blended.mgmt.ui" % "${blended.version}",
      `type` = "war"
    ),
    "org.apache.shiro" % "shiro-core" % "${shiro.version}",
    "org.apache.shiro" % "shiro-web" % "${shiro.version}",
    "de.wayofquality.blended" % "blended.security" % "${blended.version}"
  ),
  plugins = Seq(
      Plugin(
        "de.wayofquality.blended" % "blended-updater-maven-plugin" % "${blended.version}",
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
