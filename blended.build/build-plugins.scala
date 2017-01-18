
val scriptHelper =
"""
object ScriptHelper {

  import java.io.File
  import java.io.PrintWriter

  def writeFile(f: File, content: String) : Unit = {
    val action = if(f.exists()) "Overwriting file: " else "Creating file: "
    println(action + f)
    f.getParentFile().mkdirs()
    val writer = new PrintWriter(f)
    writer.print(content)
    writer.close()
  }
}
"""

// Plugins

val mavenDependencyPlugin = "org.apache.maven.plugins" % "maven-dependency-plugin" % "2.10"
val buildHelperPlugin = "org.codehaus.mojo" % "build-helper-maven-plugin" % "1.12"
val execMavenPlugin =  "org.codehaus.mojo" % "exec-maven-plugin" % "1.5.0"
val jettyMavenPlugin = "org.mortbay.jetty" % "jetty-maven-plugin" % "8.1.16.v20140903"
val mavenInstallPlugin = "org.apache.maven.plugins" % "maven-install-plugin" % "2.5.2"

val mavenBundlePlugin = Plugin(
  gav = "org.apache.felix" % "maven-bundle-plugin" % "3.2.0",
  extensions = true,
  executions = Seq(
    Execution(
      id = "manifest",
      phase = "process-classes",
      goals = Seq(
        "manifest"
      )
    )
  ),
  dependencies = Seq(
    bndLib
  ),
  configuration = Config(
    supportedProjectTypes = Config(
      supportedProjectType = "jar",
      supportedProjectType = "bundle",
      supportedProjectType = "war"
    ),
    instructions = Config(
      _include = "osgi.bnd"
    )
  )
)

val bundleWarPlugin = Plugin(
  gav = mavenBundlePlugin.gav,
  executions = Seq(
    Execution(
      id = "manifest",
      phase = "prepare-package",
      goals = Seq(
        "manifest"
      )
    )
  ),
  configuration = Config(
    supportedProjectTypes = Config(
      supportedProjectType = "war"
    ),
    instructions = Config(
      _include = "osgi.bnd"
    )
  )
)

val scalaCompilerConfig = Config(
  fork = "true",
  recompileMode = "incremental",
  useZincServer = "true",
  addJavacArgs = "-target|${java.version}|-source|${java.version}",
  addZincArgs = "-C-target|-C${java.version}|-C-source|-C${java.version}",
  args = Config(
    arg = "-deprecation",
    arg = "-feature",
    arg = "-Xlint",
    arg = "-Ywarn-nullary-override"
  ),
  jvmArgs = Config(
    jvmArg = "-Xms256m",
    jvmArg = "-Xmx512m",
    jvmArg = "-XX:MaxPermSize=128m"
  )
)

val scalaMavenPlugin = Plugin(
  gav = "net.alchim31.maven" % "scala-maven-plugin" % "3.2.1",
  executions = Seq(
    Execution(
      id = "compile-scala",
      goals = Seq(
        "compile"
      ),
      configuration = scalaCompilerConfig
    ),
    Execution(
      id = "test-compile-scala",
      goals = Seq(
        "testCompile"
      ),
      configuration = scalaCompilerConfig
    )
  ),
  configuration = Config(
    scalaVersion = BlendedVersions.scalaVersion
  )
)

val scalatestMavenPlugin = Plugin(
  "org.scalatest" % "scalatest-maven-plugin" % "1.0",
  executions = Seq(
    Execution(
      id = "test",
      goals = Seq("test")
    )
  ),
  configuration = Config(
    reportsDirectory = "${project.build.directory}/surefire-reports",
    junitxml = ".",
    stdout = "FT"
  )
)

/*
 * Some helper plugins to compile ScalaJS code with SBT.
 */

val prepareSbtPlugin = Plugin(
  gav = scalaMavenPlugin.gav,
  executions = Seq(
    Execution(
      id = "prepareSBT",
      phase = "generate-resources",
      goals = Seq(
        "script"
      ),
      configuration = Config(
        script = scriptHelper +
          """
import java.io.File

ScriptHelper.writeFile(
  new File(project.getBasedir(), "project/build.properties"),
  "sbtVersion=""" + BlendedVersions.sbtVersion + """"
)

ScriptHelper.writeFile(
  new File(project.getBasedir(), "project/plugins.sbt"),
  "resolvers += \"Typesafe repository\" at \"http://repo.typesafe.com/typesafe/releases/\"\n" +
  "\n" +
  "addSbtPlugin(\"org.scala-js\" % \"sbt-scalajs\" % \"""" + BlendedVersions.scalaJsVersion + """\")\n" +
  "\n" +
  "addSbtPlugin(\"com.typesafe.sbt\" % \"sbt-less\" % \"1.1.0\")"
)
"""
      )
    )
  )
)

val compileJsPlugin = Plugin(
  gav = execMavenPlugin,
  executions = Seq(
    Execution(
      id = "compileAndTestJs",
      phase = "compile",
      goals = Seq(
        "exec"
      ),
      configuration = Config(
        executable = "sbt",
        workingDirectory = "${project.basedir}",
        arguments = Config(
          argument = "test"
        )
      )
    ),
    Execution(
      id = "packageJS",
      phase = "package",
      goals = Seq(
        "exec"
      ),
      configuration = Config(
        executable = "sbt",
        workingDirectory = "${project.basedir}",
        arguments = Config(
          argument = "packageBin"
        )
      )
    )
  )
)

val dockerMavenPlugin = Plugin(
  "com.alexecollins.docker" % "docker-maven-plugin" % "2.11.21",
  executions = Seq(
    Execution(
      id = "clean-docker",
      phase = "clean",
      goals = Seq(
        "clean"
      )
    ),
    Execution(
      id = "package-docker",
      phase = "package",
      goals = Seq(
        "package"
      )
    ),
    Execution(
      id = "deploy-docker",
      phase = "deploy",
      goals = Seq(
        "deploy"
      )
    )
  ),
  configuration = Config(
    version = "1.24",
    username = "atooni",
    email = "andreas@wayofquality.de",
    password = "foo",
    prefix = "blended",
    host = "tcp://${docker.host}:${docker.port}",
    cleanContainerOnly = true,
    removeIntermediateImages = true,
    cache = true
  )
)
