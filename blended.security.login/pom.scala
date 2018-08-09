import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.securityLogin,
  packaging = "bundle",
  description = "OSGI Services to support a token based authentication and authorization.",
  dependencies = Seq(
    scalaLib % "provided",
    Blended.domino,
    Blended.akka,
    Blended.security,
    Deps.jjwt,
    Deps.prickle,
    bouncyCastleBcprov,
    Blended.testSupport % "test",
    Blended.testSupportPojosr % "test",
    scalaTest % "test",
    logbackCore % "test",
    logbackClassic % "test"
  ),
  plugins = Seq(
    Plugin(
      mavenBundlePlugin.gav,
      extensions = true,
      inherited = true,
      configuration = Config(
        instructions = Config(
          _include = "osgi.bnd",
          `Embed-Dependency` = s"*;artifactId=${jjwt.artifactId},artifactId=${bouncyCastleBcprov.artifactId}"
        )
      )
    ),
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
