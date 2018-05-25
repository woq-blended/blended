import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.security,
  packaging = "bundle",
  description = "Configuration bundle for the Apache Shiro security framework.",
  dependencies = Seq(
    Blended.securityBoot,
    Blended.akka,
    Blended.util,
    Blended.domino,
    log4s,
    Blended.testSupport % "test",
    Blended.testSupportPojosr % "test",
    scalaLib % "provided",
    scalaTest % "test",
    logbackCore % "test",
    logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
