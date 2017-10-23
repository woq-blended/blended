import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include blended.build/build-versions.scala
#include blended.build/build-dependencies.scala
#include blended.build/build-plugins.scala
#include blended.build/build-common.scala

val buildModules = Seq(
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
  "blended.jms.sampler",
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
  "blended.security.login",
  "blended.security.login.rest",
  "blended.security.spray",
  "blended.hawtio.login",
  "blended.persistence",
  "blended.persistence.orient",
  "blended.jolokia",
  "blended.itestsupport",
  "blended.samples",
  "blended.activemq.defaultbroker",
  "blended.activemq.client",
  "blended.launcher.features",
  "blended.mgmt.ui",
  "blended.file",
  "blended.demo"
)

val itestModules = Seq(
  "blended.itest"
)

val dockerModules = Seq(
  "blended.docker"
)

BlendedModel(
  gav = BlendedModule("blended.reactor"),
  packaging = "pom",
  description = "A collection of bundles to develop OSGi application on top of Scala and Akka and Camel.",
  plugins = Seq(
    Plugin(
      Plugins.nexusStaging,
      extensions = true,
      configuration = Config(
        serverId = "ossrh",
        nexusUrl = "https://oss.sonatype.org/",
        autoReleaseAfterClose = "true",
        skipNexusStagingDeployMojo = "true"
      )
    ),
    skipInstallPlugin,
    skipDeployPlugin
  ),
  profiles = Seq(
    Profile(
      id = "build",
      activation = Activation(
        activeByDefault = true
      ),
      modules = buildModules
    ),
    Profile(
      id = "itest",
      modules = itestModules
    ),
    Profile(
      id = "docker",
      modules = dockerModules
    ),
    Profile(
      id = "all",
      modules = buildModules ++ itestModules ++ dockerModules
    )
  )
)
