import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

object Feature {
  def apply(name: String) = Dependency(
      blendedLauncherFeatures,
      `type` = "conf",
      classifier = name
  )
}

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
    "profile.name" -> "blended-mgmt"
  ),
  plugins = Seq(
      Plugin(
        "de.wayofquality.blended" % "blended-updater-maven-plugin" % blendedVersion,
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
        "org.apache.maven.plugins" % "maven-dependency-plugin" % "2.10",
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
        "net.alchim31.maven" % "scala-maven-plugin",
        executions = Seq(
          Execution(
            id = "build-product",
            phase = "test-compile",
            goals = Seq(
              "script"
            ),
            configuration = Config(
              script = """
import java.io.File
import ammonite.ops._
import scala.collection.JavaConverters._

val launcherDir = "blended.launcher-" + project.getProperties.get("blended.version").asInstanceOf[String]

val profileName = project.getProperties.get("profile.name").asInstanceOf[String]
val profileVersion = project.getProperties.get("profile.version").asInstanceOf[String]

val projectDir = Path(project.getBasedir)
val prodDir = projectDir/'target/'product

val profDir = prodDir/'profiles/profileName/profileVersion

val prodLaunch = prodDir/"launch.conf"
val tarLaunch = projectDir/'target/'launcher/launcherDir/"launch.conf"

rm! prodDir
mkdir! prodDir/up

// copy launcher
cp(projectDir/'target/'launcher/launcherDir, prodDir)

// copy profile
mkdir! profDir/up
cp(projectDir/'target/'classes/'profile, profDir)
cp.into(projectDir/'target/'classes/'container, prodDir)

// make launchfile

val launchConf = "profile.baseDir=${BLENDED_HOME}/profiles" +
s"profile.name=${profileName}\nprofile.version=${profileVersion}"

write(prodLaunch, launchConf)
write(tarLaunch, launchConf)
"""
              )
          )
        ),
        dependencies = Seq(
          "com.lihaoyi" % "ammonite-ops_2.10" % "0.7.2"
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
