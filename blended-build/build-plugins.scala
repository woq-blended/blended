// Plugins

val mavenBundlePluginVersion = "3.2.0"

val mavenBundlePlugin = Plugin(
  "org.apache.felix" % "maven-bundle-plugin" % mavenBundlePluginVersion,
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
  "org.apache.felix" % "maven-bundle-plugin" % mavenBundlePluginVersion,
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

val compileJsPlugin = Plugin(
  "org.codehaus.mojo" % "exec-maven-plugin" % "1.5.0",
  executions = Seq(
    Execution(
      id = "compileJS",
      phase = "compile",
      goals = Seq(
        "exec"
      ),
      configuration = Config(
        executable = "sbt",
        workingDirectory = "${project.basedir}",
        arguments = Config(
          argument = "fullOptJS"
        )
      )
    )
  )
)

val scalaMavenPlugin = Plugin(
  "net.alchim31.maven" % "scala-maven-plugin" % "3.2.1",
  executions = Seq(
    Execution(
      id = "compile-scala",
      goals = Seq(
        "compile"
      ),
      configuration = Config(
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
    ),
    Execution(
      id = "test-compile-scala",
      goals = Seq(
        "testCompile"
      ),
      configuration = Config(
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
    )
  ),
  configuration = Config(
    scalaVersion = scalaVersion.version
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

val buildHelperPlugin = "org.codehaus.mojo" % "build-helper-maven-plugin" % "1.12"
val execMavenPlugin =  "org.codehaus.mojo" % "exec-maven-plugin" % "1.5.0"
