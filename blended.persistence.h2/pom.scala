import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.persistenceH2,
  packaging = "bundle",
  description = "Implement a persistence backend with OrientDB.",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Blended.persistence,
    Blended.utilLogging,
    Deps.slf4j,
    Deps.domino,
    Deps.orientDbCore,
    Deps.h2,
    Deps.hikaricp,
    Deps.springBeans,
    Deps.springCore,
    Deps.springTx,
    Deps.springJdbc,
    Deps.liquibase,
    Deps.snakeyaml,
    Deps.jclOverSlf4j % "runtime",
    Deps.scalaTest % "test",
    Blended.testSupport.intransitive % "test",
    Deps.logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin,
    Plugin(
      Plugins.surefire,
      executions = Seq(
        Execution(
          id = "default-test",
          phase = "none"
        )
      )
    )
  )
)
