import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.prickle,
  packaging = "bundle",
  description = "Wrapper for Prickle and mircojson",
  dependencies = Seq(
    prickle,
    microjson
  ),
  plugins = Seq(
    Plugin(
      gav = mavenBundlePlugin.gav,
      extensions = true,
      configuration = Config(
        instructions = Config(
          `Bundle-Version` = Blended.prickle.version,
          `Bundle-SymbolicName` = Blended.prickle.artifactId,
          `Import-Package` = "scala.*;version=\"[" + scalaVersion.binaryVersion + "," + scalaVersion.binaryVersion + ".50)\",prickle,microjson,*",
          `Embed-Dependency` = prickle.artifactId + ";inline=true," + microjson.artifactId + ";inline=true",
          `Export-Package` = "prickle;version=\"" + prickle.version.get + "\",microjson;version=\"" + microjson.version.get + "\""
        )
      )
    )
  )
)
