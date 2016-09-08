import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-common.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala

object Feature {
  def apply(name: String) = Dependency(
      blendedLauncherFeatures,
      `type` = "conf",
      classifier = name
  )
}

val profileName = "blended-mgmt"

BlendedModel(
  gav = blendedDemoMgmt,
  packaging = "jar",
  description = "A sample container for the blended launcher",
  prerequisites = Prerequisites(
    maven = "3.3.3"
  ),
  dependencies = Seq(
    Dependency(
      blendedLauncher,
      `type` = "zip",
      classifier = "bin"
    ),
    Feature("blended-base"),
    Feature("blended-commons"),
    Feature("blended-http"),
    Feature("blended-jetty"),
    Feature("blended-jaxrs"),
    Feature("blended-spray"),
    Feature("blended-hawtio"),
    Feature("blended-spring"),
    Feature("blended-activemq"),
    Feature("blended-camel"),
    Feature("blended-samples"),
    Feature("blended-mgmt-server"),
    blendedMgmtRest,
    blendedUpdaterRemote
  ),
  properties = Map(
    "profile.version" -> blendedDemoMgmt.version.get,
    "profile.name" -> "blended-mgmt",
    "spray.version" -> BlendedVersions.sprayVersion,
    "loglevel.test" -> "DEBUG"
  ),
  plugins = Seq(
      Plugin(
        gav = blendedUpdaterMavenPlugin,
        executions = Seq(
          Execution(
            id = "materialize-profile",
            phase = "compile",
            goals = Seq(
              "materialize-profile"
            ),
            configuration = Config(
              srcProfile = "${project.build.directory}/classes/profile/profile.conf",
              destDir = "${project.build.directory}/classes/profile"
            )
          )
        )
      ),
      Plugin(
        gav = mavenDependencyPlugin,
        executions = Seq(
          Execution(
            id = "unpack-launcher",
            phase = "compile",
            goals = Seq(
              "unpack"
            ),
            configuration = Config(
              artifactItems = Config(
                artifactItem = Config(
                  groupId = "${project.groupId}",
                  artifactId = "blended.launcher",
                  classifier = "bin",
                  `type` = "zip",
                  outputDirectory = "${project.build.directory}/launcher"
                )
              )
            )
          )
        )
      ),
      Plugin(
        gav = scalaMavenPlugin.gav,
        executions = Seq(
          Execution(
            id = "build-product",
            phase = "generate-resources",
            goals = Seq(
              "script"
            ),
            configuration = Config(
              script = scriptHelper + """
import java.io.File

// make launchfile

val tarLaunchFile = new File(project.getBasedir(), "target/classes/container/launch.conf")

val launchConf =
  "profile.baseDir=${BLENDED_HOME}/profiles\n" +
  "profile.name=""" + profileName + """\n" +
  "profile.version=""" + blendedDemoMgmt.version.get + """"

ScriptHelper.writeFile(tarLaunchFile, launchConf)

// make overlays base.conf

val baseConfFile = new File(project.getBasedir(), "target/classes/profile/overlays/base.conf")
ScriptHelper.writeFile(baseConfFile, "overlays = []")
"""
              )
          )
        )
      ),
      Plugin(
        "org.apache.maven.plugins" % "maven-assembly-plugin",
        executions = Seq(
          Execution(
            id = "assemle",
            phase = "package",
            goals = Seq(
              "single"
            )
          )
        ),
        configuration = Config(
          descriptors = Config(
            descriptor = "src/main/assembly/full-nojre.xml",
            descriptor = "src/main/assembly/product.xml"
          )
        )
      ),
      Plugin(
        "org.apache.maven.plugins" % "maven-jar-plugin" % "2.6",
        executions = Seq(
          Execution(
            id = "default-jar",
            phase = "none"
          )
        )
      )
  ),
  profiles = Seq(
    Profile(
      id = "release",
      activation = Activation(
      ),
      build = BuildBase(
        plugins = Seq(
          Plugin(
            "org.sonatype.plugins" % "nexus-staging-maven-plugin",
            configuration = Config(
              skipNexusStagingDeployMojo = "true"
            )
          )
        )
      )
    )
  )
)
