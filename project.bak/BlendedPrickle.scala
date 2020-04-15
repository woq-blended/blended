import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import com.typesafe.sbt.osgi.OsgiKeys
import phoenix.ProjectFactory
import sbt.Keys._
import sbt._

object BlendedPrickle extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.prickle"
    override val description : String = "OSGi package for Prickle and mircojson"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.prickle.intransitive(),
      Dependencies.microjson.intransitive()
    )

    override def bundle : OsgiBundle = super.bundle.copy(
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
