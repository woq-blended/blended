import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.securityScep,
  packaging = "bundle",
  description = "Bundle to manage the container certificate via SCEP.",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Deps.scalaReflect % "provided",
    Blended.domino,
    Deps.commonsCodec,
    Blended.securitySsl,
    Deps.bouncyCastleBcprov,
    Deps.bouncyCastlePkix,
    Blended.util,
    Deps.slf4j,
    Blended.utilLogging,
    Deps.jcip,
    Deps.jscep,
    Deps.commonsIo,
    Deps.commonsLang2,
    Deps.logbackCore % "test",
    Deps.logbackClassic % "test",
    Deps.scalaTest % "test"
  ),
  plugins = Seq(
    Plugin(
      mavenBundlePlugin.gav,
      extensions = true,
      configuration = Config(
        instructions = Config(
          _include = "osgi.bnd",
          `Embed-Dependency` = s"*;artifactId=${
            Seq(
              Deps.commonsIo,
              Deps.commonsLang2,
              Deps.commonsCodec,
              Deps.jcip,
              Deps.jscep,
              Deps.bouncyCastleBcprov,
              Deps.bouncyCastlePkix
            ).map(_.artifactId).mkString(",")
          }"
        )
      )
    ),
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
