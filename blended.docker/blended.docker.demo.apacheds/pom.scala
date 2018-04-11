import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../../blended.build/build-versions.scala
//#include ../../blended.build/build-dependencies.scala
//#include ../../blended.build/build-plugins.scala
//#include ../../blended.build/build-common.scala

BlendedModel(
  gav = blendedDockerApacheDS,
  packaging = "jar",
  description = """A simple docker container with providing an Apache Directory Service LDAP service.""",
  plugins = Seq(
    dockerMavenPlugin
  )
)
