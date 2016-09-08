import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-versions.scala
#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedMgmtUi,
  packaging = "war",
  description = "The blended management UI.",
  build = Build(
    plugins = Seq(
      compileJsPlugin,
      bundleWarPlugin,
      Plugin(
        "org.apache.maven.plugins" % "maven-assembly-plugin" % "2.6",
        executions = Seq(
          Execution(
            id = "prepareWar",
            phase = "prepare-package",
            goals = Seq(
              "single"
            ),
            configuration = Config(
              descriptors = Config(
                descriptor = "src/main/assembly/assembly.xml"
              )
            )
          )
        )
      ),
      Plugin(
        "org.apache.maven.plugins" % "maven-war-plugin",
        configuration = Config(
          archive = Config(
            manifestFile = "${project.build.outputDirectory}/META-INF/MANIFEST.MF"
          ),
          webResources = Config(
            resource = Config(
              directory = "${project.build.directory}/${project.artifactId}-${project.version}-preWar/${project.artifactId}-${project.version}",
              targetPath = "/",
              includes = Config(
                include = "**/*"
              )
            )
            // FIXME TODO
//            resource = Config(
//              directory = "${project.build.directory}/web/classes/main/META-INF/resources/webjars/blended-mgmt-ui/${project.version}",
//              targetPath = "css"
//            )
          )
        )
      ),
      Plugin(
        "org.mortbay.jetty" % "jetty-maven-plugin" % "8.1.16.v20140903",
        configuration = Config(
          scanIntervalSeconds = "10",
          webAppSourceDirectory = "target/blended.mgmt.ui-2.0-SNAPSHOT",
          webApp = Config(
            contextPath = "/management"
          )
        )
      )
    )
  )
)
