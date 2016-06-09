// Polyglot Maven Scala file
// Shared build settings

val blendedGroupId = "de.wayofquality.blended"
val blendedVersion = "2.0-SNAPSHOT"

implicit val scalaVersion = ScalaVersion("2.11.8")

// Blended Projects

val blendedParent = Parent(
  gav = blendedGroupId % "blended.parent" % blendedVersion,
  relativePath = "../blended-parent"
)

val blendedAkka = blendedGroupId % "blended.akka" % blendedVersion
val blendedContainerContext = blendedGroupId % "blended.container.context" % blendedVersion
val blendedJmx = blendedGroupId % "blended.jmx" % blendedVersion
val blendedLauncher = blendedGroupId % "blended.launcher" % blendedVersion
val blendedMgmtBase = blendedGroupId % "blended.mgmt.base" % blendedVersion
val blendedPersistence = blendedGroupId % "blended.persistence" % blendedVersion
val blendedPersistenceOrient = blendedGroupId % "blended.persistence.orient" % blendedVersion
val blendedSprayApi = blendedGroupId % "blended.spray.api" % blendedVersion
val blendedTestSupport = blendedGroupId % "blended.testsupport" % blendedVersion
val blendedUpdater = blendedGroupId % "blended.updater" % blendedVersion
val blendedUpdaterConfig = blendedGroupId % "blended.updater.config" % blendedVersion

// Dependencies

val akkaOsgi = "com.typesafe.akka" %% "akka-osgi" % "${akka.version}"
val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "${akka.version}"
val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % "${akka.version}"

val domino = "com.github.domino-osgi" %% "domino" % "1.1.1"

val felixFramework = "org.apache.felix" % "org.apache.felix.framework" % "5.0.0"
val felixFileinstall = "org.apache.felix" % "org.apache.felix.fileinstall" % "${felix.fileinstall.version}"
val felixGogoCommand = "org.apache.felix" % "org.apache.felix.gogo.command" % "0.14.0"
val felixGogoShell = "org.apache.felix" % "org.apache.felix.gogo.shell" % "0.10.0"
val felixGogoRuntime = "org.apache.felix" % "org.apache.felix.gogo.runtime" % "0.16.2"

val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.3"

val mockitoAll = "org.mockito" % "mockito-all" % "1.9.5" 

val orientDbCore = "com.orientechnologies" % "orientdb-core" % "2.2.0"

val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4"
val slf4jVersion = "1.7.12"
val slf4j = "org.slf4j" % "slf4j-api" % slf4jVersion

val orgOsgi = "org.osgi" % "org.osgi.core" % "5.0.0"

val typesafeConfig = "com.typesafe" % "config" % "${typesafe.config.version}"

// Plugins

val mavenBundlePlugin = Plugin(
  "org.apache.felix" % "maven-bundle-plugin",
  extensions = true
)

val scalaMavenPlugin = Plugin(
  "net.alchim31.maven" % "scala-maven-plugin"
)

val scalatestMavenPlugin = Plugin(
  "org.scalatest" % "scalatest-maven-plugin"
)

def generatePomXml(phase: String = "validate") = Plugin(
  "io.takari.polyglot" % "polyglot-translate-plugin" % "0.1.15",
  executions = Seq(
    Execution(
      id = "generate-pom.xml",
      goals = Seq("translate"),
      phase = phase,
      configuration = Config(
        input = "pom.scala",
        output = "pom.xml"
      )
    )
  )
)
