import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.securitySsl,
  packaging = "bundle",
  description = "Bundle to provide simple Server Certificate Management.",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Deps.scalaReflect % "provided",
    Deps.bouncyCastleBcprov % "provided",
    Deps.bouncyCastlePkix,
    Blended.domino,
    Dependency(Deps.domino).intransitive,
    Blended.mgmtBase,
    Blended.utilLogging,
    logbackCore % "test",
    logbackClassic % "test",
    scalaTest % "test",
    Blended.testSupport % "test",
    Blended.testSupportPojosr % "test"
  ),
  plugins = Seq(
    Plugin(
      mavenBundlePlugin.gav,
      extensions = true,
      inherited = true,
      configuration = Config(
        instructions = Config(
          _include = "osgi.bnd",
          `Embed-Dependency` = s"*;artifactId=${bouncyCastleBcprov.artifactId},${bouncyCastlePkix.artifactId}"
        )
      )
    ),
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
