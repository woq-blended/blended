import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include blended-build/build-common.scala

Model(
  gav = BlendedModule("blended.reactor"),
  packaging = "pom",
  name = "${project.artifactId}",
  description = "A collection of bundles to develop OSGi application on top of Scala and Akka and Camel.",
  url = "https://github.com/woq-blended/blended",
  developers = BlendedModel.defaultDevelopers,
  licenses = BlendedModel.defaultLicenses,
  scm = BlendedModel.scm,
  organization = BlendedModel.organization,
  distributionManagement = DistributionManagement(
    repository = DeploymentRepository(
      id = "ossrh",
      url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
    ),
    snapshotRepository = DeploymentRepository(
      id = "ossrh",
      url = "https://oss.sonatype.org/content/repositories/snapshots/"
    )
  ),
  build = Build(
    plugins = Seq(
      Plugin(
        "org.sonatype.plugins" % "nexus-staging-maven-plugin" % "1.6.5",
        extensions = true,
        configuration = Config(
          serverId = "ossrh",
          nexusUrl = "https://oss.sonatype.org/",
          autoReleaseAfterClose = "true"
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
    )
  ),
  profiles = Seq(
    Profile(
      id = "sign",
      activation = Activation(
      ),
      build = BuildBase(
        plugins = Seq(
          Plugin(
            "org.apache.maven.plugins" % "maven-gpg-plugin" % "1.6",
            executions = Seq(
              Execution(
                id = "sign-artifacts",
                phase = "verify",
                goals = Seq(
                  "sign"
                )
              )
            )
          )
        )
      )
    ),
    Profile(
      id = "parent",
      modules = Seq(
        "blended-parent"
      )
    ),
    Profile(
      id = "build",
      modules = Seq(
        "blended-launcher",
        "blended-updater",
        "blended-updater-config",
        "blended-updater-tools",
        "blended-updater-remote",
        "blended-updater-maven-plugin",
        "blended-activemq-brokerstarter",
        "blended-container-context",
        "blended-container-registry",
        "blended-util",
        "blended-jmx",
        "blended-camel-utils",
        "blended-jms-utils",
        "blended-testsupport",
        "blended-domino",
        "blended-akka",
        "blended-mgmt-base",
        "blended-mgmt-repo",
        "blended-mgmt-repo-rest",
        "blended-mgmt-agent",
        "blended-mgmt-rest",
        "blended-mgmt-mock",
        "blended-spray-api",
        "blended-spray",
        "blended-security",
        "blended-security-boot",
        "blended-hawtio-login",
        "blended-persistence",
        "blended-persistence-orient",
        "blended-jolokia",
        "blended-itestsupport",
        "blended-samples",
        "blended-activemq-defaultbroker",
        "blended-activemq-client",
        "blended-launcher-features",
        "blended-demo-launcher",
        "blended-demo-mgmt",
        "blended-mgmt-ui"
      )
    ),
    Profile(
      id = "itest",
      modules = Seq(
        "blended-itestsupport",
        "blended-akka-itest"
      )
    ),
    Profile(
      id = "docker",
      modules = Seq(
        "blended-docker"
      )
    ),
    Profile(
      id = "release",
      build = BuildBase(
        plugins = Seq(
          Plugin(
            "org.apache.maven.plugins" % "maven-gpg-plugin" % "1.6",
            executions = Seq(
              Execution(
                id = "sign-artifacts",
                phase = "verify",
                goals = Seq(
                  "sign"
                )
              )
            )
          ),
          Plugin(
            "org.sonatype.plugins" % "nexus-staging-maven-plugin" % "1.6.5",
            extensions = true,
            configuration = Config(
              serverId = "ossrh",
              nexusUrl = "https://oss.sonatype.org/",
              autoReleaseAfterClose = "true"
            )
          )
        )
      )
    )
  ),
  modelVersion = "4.0.0"
)
