import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.securityScepStandalone,
  packaging = "jar",
  description = "Standalone client to manage certificates via SCEP",
  dependencies = Seq(
    Deps.scalaLib,
    Deps.scalaReflect,
//    Blended.domino,
//    Deps.commonsCodec,
//    Blended.securitySsl,
//    Deps.bouncyCastleBcprov,
//    Deps.bouncyCastlePkix,
//    Blended.util,
//    Deps.slf4j,
//    Deps.jcip,
//    Deps.scep,
//    Deps.commonsIo,
//    Deps.commonsLang,
    Deps.felixConnect,
    Blended.securityScep,
    Blended.containerContextImpl,
    Deps.log4s,
    Deps.logbackCore % "runtime",
    Deps.logbackClassic % "runtime",
    Deps.scalaTest % "test"
  ),
  plugins = Seq(
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
