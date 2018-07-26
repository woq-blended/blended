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
    Deps.felixConnect.intransitive,
    Blended.securityScep.intransitive,
    Blended.securitySsl.intransitive,
    Blended.containerContextImpl.intransitive,
    Deps.log4s.intransitive,
    Deps.domino.intransitive,
    Deps.typesafeConfig.intransitive,
    Blended.containerContextApi.intransitive,
    Deps.slf4j.intransitive,
    Blended.domino.intransitive,
    Deps.orgOsgi.intransitive,
    Blended.updaterConfig.intransitive % "runtime",
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
    scalatestMavenPlugin,
    Plugin(
      Plugins.jar,
      configuration = Config(
        archive = Config(
          manifest = Config(
            addClasspath = "true",
            classpathPrefix = "libs/",
            mainClass = "blended.security.scep.standalone.ScepClientApp"
          )
        )
      )
    ),
    Plugin(
      gav = Plugins.assembly,
      executions = Seq(
        Execution(
          id = "assembly-app",
          phase = "package",
          goals = Seq(
            "single"
          )
        )
      ),
      configuration = Config(
        descriptors = Config(
          descriptor = "src/app/assembly/bin.xml"
        )
      )
    )
  )
)
