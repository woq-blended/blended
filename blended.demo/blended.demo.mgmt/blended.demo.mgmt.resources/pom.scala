import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../../../blended.build/build-versions.scala
//#include ../../../blended.build/build-dependencies.scala
//#include ../../../blended.build/build-plugins.scala
//#include ../../../blended.build/build-common.scala

BlendedProfileResourcesContainer (
  gav = blendedDemoMgmtResources,
  properties = Map(
    "spray.version" -> BlendedVersions.sprayVersion
  )
)
