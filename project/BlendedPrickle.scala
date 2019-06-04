import blended.sbt.Dependencies
import com.typesafe.sbt.osgi.OsgiKeys
import phoenix.ProjectFactory
import sbt.Keys._
import sbt._

object BlendedPrickle extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.prickle"
    override val description = "OSGi package for Prickle and mircojson"

    override def deps = Seq(
      Dependencies.prickle.intransitive(),
      Dependencies.microjson.intransitive()
    )

    override def bundle = super.bundle.copy(
      importPackage = Seq(
        "prickle",
        "microjson"
      ),
      privatePackage = Seq.empty,
      exportContents = Seq(
        s"prickle;version=${Dependencies.prickleVersion};-split-package:=merge-first",
        s"microjson;version=${Dependencies.microJsonVersion};-split-package:=merge-first"
      )
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      OsgiKeys.embeddedJars := {
        (Compile / externalDependencyClasspath).value.map(_.data)
          .filter { f =>
            f.getName().startsWith("prickle") || f.getName().startsWith("microjson")
          }
      }
    )
  }
}
