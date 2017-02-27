import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include blended.build/build-versions.scala
#include blended.build/build-dependencies.scala
#include blended.build/build-plugins.scala
#include blended.build/build-common.scala

BlendedModel(
  gav = BlendedModule("blended.reactor"),
  packaging = "pom",
  description = "A collection of bundles to develop OSGi application on top of Scala and Akka and Camel.",
  plugins = Seq(
    Plugin(
      "org.sonatype.plugins" % "nexus-staging-maven-plugin",
      extensions = true,
      configuration = Config(
        serverId = "ossrh",
        nexusUrl = "https://oss.sonatype.org/",
        autoReleaseAfterClose = "true",
        skipNexusStagingDeployMojo = "true"
      )
    ),
    Plugin(
      "org.apache.maven.plugins" % "maven-install-plugin" % "2.4",
      configuration = Config(
        skip = "true"
      )
    ),
    Plugin(
      "org.apache.maven.plugins" % "maven-deploy-plugin" % "2.7",
      configuration = Config(
        skip = "true"
      )
    )
  ),
  profiles = Seq(
    Profile(
      id = "build",
      activation = Activation(
        activeByDefault = true
      ),
      modules = Seq(
        "blended.launcher",
        "blended.updater",
        "blended.updater.config",
        "blended.updater.tools",
        "blended.updater.remote",
        "blended-updater-maven-plugin",
        "blended.activemq.brokerstarter",
        "blended.container.context",
        "blended.container.registry",
        "blended.util",
        "blended.jmx",
        "blended.camel.utils",
        "blended.jms.utils",
        "blended.testsupport",
        "blended.domino",
        "blended.akka",
        "blended.mgmt.base",
        "blended.mgmt.repo",
        "blended.mgmt.repo.rest",
        "blended.mgmt.agent",
        "blended.mgmt.rest",
        "blended.mgmt.mock",
        "blended.mgmt.service.jmx",
        "blended.prickle",
        "blended.spray.api",
        "blended.spray",
        "blended.security",
        "blended.security.boot",
        "blended.hawtio.login",
        "blended.persistence",
        "blended.persistence.orient",
        "blended.jolokia",
        "blended.itestsupport",
        "blended.samples",
        "blended.activemq.defaultbroker",
        "blended.activemq.client",
        "blended.launcher.features",
        "blended.demo",
        "blended.mgmt.ui"
      )
    ),
    Profile(
      id = "itest",
      modules = Seq(
        "blended.itestsupport",
        "blended.akka.itest"
      )
    ),
    Profile(
      id = "docker",
      modules = Seq(
        "blended.docker"
      )
    )
  )
)
