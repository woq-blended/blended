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
    Deps.felixConnect.intransitive,
    Blended.securityScep.intransitive,
    Blended.securitySsl.intransitive,
    Blended.containerContextImpl.intransitive,
    Deps.log4s.intransitive,
    Deps.domino.intransitive,
    Deps.typesafeConfig.intransitive,
    Blended.containerContextApi.intransitive,
    Deps.slf4j.intransitive,
    Blended.updaterConfig.intransitive % "runtime",
    Blended.domino.intransitive % "runtime",
    Deps.orgOsgi.intransitive % "runtime",
    Deps.jcip.intransitive % "runtime",
    Deps.jscep.intransitive % "runtime",
    Deps.bouncyCastlePkix.intransitive % "runtime",
    Deps.bouncyCastleBcprov.intransitive % "runtime",
    Deps.commonsIo.intransitive % "runtime",
    Deps.commonsLang2.intransitive % "runtime",
    Deps.commonsCodec.intransitive % "runtime",
    Deps.logbackCore.intransitive % "runtime",
    Deps.logbackClassic.intransitive % "runtime",
    Deps.scalaTest % "test"
  ),
  plugins = Seq(
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
