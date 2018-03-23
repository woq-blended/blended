import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedSecuritySsl,
  packaging = "bundle",
  description = "Bundle to provide simple Server Certificate Management.",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Deps.scalaReflect % "provided",
    Deps.bouncyCastleBcprov % "provided",
    Deps.bouncyCastlePkix,
    blendedDomino,
    Dependency(Deps.domino).pure,
    blendedMgmtBase,
    Deps.log4s,
    logbackCore % "test",
    logbackClassic % "test",
    scalaTest % "test",
    blendedTestSupport % "test",
    blendedTestSupportPojosr % "test"
  ),
  plugins = Seq(
    Plugin(
      mavenBundlePlugin.gav,
      extensions = true,
      inherited = true,
      configuration = Config(
        instructions = new Config(Seq(
          "_include" -> Option("osgi.bnd"),
          "Embed-Dependency" -> Option(s"*;artifactId=${bouncyCastleBcprov.artifactId},${bouncyCastlePkix.artifactId}")
        ))
      )
    ),
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
