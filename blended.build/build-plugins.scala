val ivy2Repo = System.getProperty("ivy2.repo.local", System.getProperty("user.home") + "/.ivy2")
val m2Repo = System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository")

/**
 * Useful helper methods that can be used inside scala scripts (scala-maven-plugin:script).
 */
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

/**
 * Used Maven plugins
 */
object Plugins {
  val mavenPluginGroup = "org.apache.maven.plugins"

  val clean = mavenPluginGroup % "maven-clean-plugin" % "3.0.0"
  val assembly = mavenPluginGroup % "maven-assembly-plugin" % "3.1.0"
  val compiler = mavenPluginGroup % "maven-compiler-plugin" % "3.5.1"
  val dependency = mavenPluginGroup % "maven-dependency-plugin" % "2.10"
  val deploy = mavenPluginGroup % "maven-deploy-plugin" % "2.8.2"
  val enforcer = mavenPluginGroup % "maven-enforcer-plugin" % "1.3.1"
  val gpg = mavenPluginGroup % "maven-gpg-plugin" % "1.6"
  val install = mavenPluginGroup % "maven-install-plugin" % "2.5.2"
  val jar = mavenPluginGroup % "maven-jar-plugin" % "2.6"
  val plugin = mavenPluginGroup % "maven-plugin-plugin" % "3.2"
  val resources = mavenPluginGroup % "maven-resources-plugin" % "3.0.1"
  val source = mavenPluginGroup % "maven-source-plugin" % "3.0.1"
  val war = mavenPluginGroup % "maven-war-plugin" % "3.0.0"

  val buildHelper = "org.codehaus.mojo" % "build-helper-maven-plugin" % "3.0.0"
  val bundle = "org.apache.felix" % "maven-bundle-plugin" % "3.2.0"
  val dependencyCheck = "org.owasp" % "dependency-check-maven" % "3.0.1"
  val docker = "com.alexecollins.docker" % "docker-maven-plugin" % "2.11.24"
  val exec = "org.codehaus.mojo" % "exec-maven-plugin" % "1.5.0"
  val jetty = "org.mortbay.jetty" % "jetty-maven-plugin" % "8.1.16.v20140903"
  val nexusStaging = "org.sonatype.plugins" % "nexus-staging-maven-plugin" % "1.6.8"
  val polyglot = "io.takari.polyglot" % "polyglot-translate-plugin" % "0.2.1"
  val sbtCompiler = "com.google.code.sbt-compiler-maven-plugin" % "sbt-compiler-maven-plugin" % "1.0.0"
  val scala = "net.alchim31.maven" % "scala-maven-plugin" % "3.2.1"
  val scalaTest = "org.scalatest" % "scalatest-maven-plugin" % "1.0"
  val scoverage = "org.scoverage" % "scoverage-maven-plugin" % "1.3.1-SNAPSHOT"

  val site = mavenPluginGroup % "maven-site-plugin" % "3.3"
  val projectReports = mavenPluginGroup % "maven-project-info-reports-plugin" % "2.9"
}

val skipInstallPlugin = Plugin(
  Plugins.install,
  configuration = Config(
    skip = "true"
  )
)

val skipDeployPlugin = Plugin(
  Plugins.deploy,
  configuration = Config(
    skip = "true"
  )
)

val skipDefaultJarPlugin = Plugin(
  gav = Plugins.jar,
  executions = Seq(
    Execution(
      id = "default-jar",
      phase = "none"
    )
  )
)

val checkDepsPlugin = Plugin(
  gav = Plugins.dependencyCheck,
  executions = Seq(
    Execution(
      goals = Seq("check"),
      configuration = Config(
        failBuildOnCVSS = "11"
      )
    )
  )
)

val skipNexusStagingPlugin = Plugin(
  gav = Plugins.nexusStaging,
  configuration = Config(
    skipNexusStagingDeployMojo = "true"
  )
)

val mavenBundlePlugin = Plugin(
  gav = Plugins.bundle,
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
  gav = Plugins.bundle,
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
      _wab = "src/main/webapp,src/main/resources",
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

val sbtCompilerExecution_addSource = Execution(
  id = "add-source",
  goals = Seq("addScalaSources"),
  phase = "initialize"
)

val sbtCompilerExecution_compile = Execution(
  id = "compile",
  goals = Seq("compile", "testCompile"),
  configuration = Config(
    scalacOptions = "-deprecation -feature -Xlint -Ywarn-nullary-override",
    scalaVersion = BlendedVersions.scalaVersion
  )
)

val sbtCompilerPlugin = Plugin(
  gav = Plugins.sbtCompiler,
  executions = Seq(
    sbtCompilerExecution_addSource,
    sbtCompilerExecution_compile
  )
)

val scalatestConfiguration = Config(
  reportsDirectory = "${project.build.directory}/surefire-reports",
  junitxml = ".",
  stdout = "FT",
  systemProperties = Config(
    projectTestOutput = "${project.build.testOutputDirectory}"
  )
)

val scalatestExecution =
  Execution(
    id = "test",
    goals = Seq("test")
  )

val scalatestMavenPlugin = Plugin(
  gav = Plugins.scalaTest,
  executions = Seq(
    scalatestExecution
  ),
  configuration = scalatestConfiguration
)

val polyglotTranslatePlugin = Plugin(
  gav = Plugins.polyglot,
  // we need this dependency, because somehow without, a too old version (1.1) is used which lacks required classes
  dependencies = Seq(
    "org.codehaus.plexus" % "plexus-utils" % "3.0.24"
  ),
  executions = Seq(
    Execution(
      id = "pom-scala-to-pom-xml",
      phase = "initialize",
      goals = Seq("translate-project"),
      configuration = Config(
        input = "pom.scala",
        output = "pom.xml"
      )
    )
  )
)

/*
 * Some helper plugins to compile ScalaJS code with SBT.
 */

val scalaExecution_prepareSbt: Execution = Execution(
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
  "addSbtPlugin(\"org.scala-js\" % \"sbt-scalajs\" % \"""" + BlendedVersions.scalaJsVersion + """\")\n"
 )
"""
  )
)

def execExecution(executable: String, execId: String, phase: String, args: List[String]): Execution = {

  val cfg = new Config(args.map(a => ("argument", Some(a))))

  Execution(
    id = execId,
    phase = phase,
    goals = Seq(
      "exec"
    ),
    configuration = Config(
      executable = executable,
      workingDirectory = "${project.basedir}",
      arguments = cfg
    )
  )

}

def execExecution_compileJs(execId: String, phase: String, args: List[String]): Execution = {
  val defArgs: List[String] = List("-ivy", ivy2Repo, s"-Dmaven.repo.local=${m2Repo}")
  execExecution("sbt", execId, phase, defArgs ::: args)
}

val dockerMavenPlugin = Plugin(
  gav = Plugins.docker,
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
