import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

def artifactItem(dep: Dependency, targetDir: String): Config = {
  Config(
    groupId = dep.gav.groupId.get,
    artifactId = dep.gav.artifactId,
    version = dep.gav.version.get,
    outputDirectory = targetDir
  )

}

BlendedModel(
  gav = Blended.launcher,
  packaging = "bundle",
  description = "Provide an OSGi Launcher",
  dependencies = Seq(
    Deps.scalaLib,
    Deps.orgOsgi,
    Deps.slf4j,
    Deps.logbackCore,
    Deps.logbackClassic,
    Deps.typesafeConfig,
    Deps.commonsDaemon,
    Blended.updaterConfig,
    Deps.cmdOption,
    Blended.testSupport % "test",
    Deps.scalaTest % "test"
  ),
  properties = Map(
    "blended.launcher.version" -> Blended.launcher.version.get,
    "blended.updater.config.version" -> Blended.updaterConfig.version.get,
    "cmdoption.version" -> cmdOption.version.get,
    "org.osgi.core.version" -> orgOsgi.version.get,
    "scala.library.version" -> scalaLib.version.get,
    "typesafe.config.version" -> typesafeConfig.version.get,
    "slf4j.version" -> slf4j.version.get,
    "logback.version" -> logbackClassic.version.get
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    // Scalatest, we need to fork the tests, as Laucher depends on sys properties, which we mutate in tests
    Plugin(
      gav = Plugins.scalaTest,
      executions = Seq(
        scalatestExecution
      ),
      configuration = new Config(scalatestConfiguration.elements ++ Config(
        // What we want!
        // forkMode = "always"
        // What we can get
        forkMode = "once",
        parallel = "false",
        logForkedProcessCommand = "true"
      ).elements)
    ),
    Plugin(
      gav = Plugins.resources,
      executions = Seq(
        Execution(
          id = "runner-resources",
          phase = "process-resources",
          goals = Seq("copy-resources"),
          configuration = Config(
            outputDirectory = "${project.build.directory}/runner-resources",
            resources = Config(
              resource = Config(
                directory = "src/runner/resources",
                filtering = true
              ),
              resource = Config(
                directory = "src/runner/binaryResources",
                filtering = false
              )
            ),
            delimiters = Config(
              delimiter = "@"
            )
          )
        )
      )
    ),
    Plugin(
      gav = Plugins.assembly,
      executions = Seq(
        Execution(
          id = "bin",
          phase = "package",
          goals = Seq(
            "single"
          )
        )
      ),
      configuration = Config(
        descriptors = Config(
          descriptor = "src/main/assembly/bin.xml"
        )
      )
    ),
    Plugin(
      Plugins.dependency,
      executions = Seq(
        Execution(
          phase = "generate-test-resources",
          goals = Seq("copy"),
          configuration = Config(
            artifactItems = Config(
              artifactItem = artifactItem("org.apache.felix" % "org.apache.felix.framework" % "5.0.0", "${project.build.directory}/test-felix"),
              artifactItem = artifactItem("org.apache.felix" % "org.apache.felix.framework" % "5.6.10", "${project.build.directory}/test-felix"),

              artifactItem = artifactItem("org.eclipse" % "org.eclipse.osgi" % "3.8.0.v20120529-1548", "${project.build.directory}/test-equinox"),
              artifactItem = artifactItem("org.osgi" % "org.eclipse.osgi" % "3.10.100.v20150529-1857", "${project.build.directory}/test-equinox"),
              artifactItem = artifactItem("org.eclipse.platform" % "org.eclipse.osgi" % "3.12.50", "${project.build.directory}/test-equinox"),
              artifactItem = artifactItem("org.eclipse.birt.runtime" % "org.eclipse.osgi" % "3.9.1.v20130814-1242", "${project.build.directory}/test-equinox"),
              artifactItem = artifactItem("org.eclipse.birt.runtime" % "org.eclipse.osgi" % "3.10.0.v20140606-1445", "${project.build.directory}/test-equinox")

            )
          )
        )
      )
    )
  )
)
