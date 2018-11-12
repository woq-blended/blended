import com.typesafe.sbt.osgi.OsgiKeys
import sbt.Keys._
import sbt._

object BlendedPrickle extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.prickle",
    description = "OSGi package for Prickle and mircojson",
    deps = Seq(
      Dependencies.prickle.intransitive(),
      Dependencies.microjson.intransitive()
    ),
    adaptBundle = b => b.copy(
      importPackage = Seq(
        "prickle",
        "microjson"
      ),
      privatePackage = Seq.empty,
      exportContents = Seq(
        s"prickle;version=${Dependencies.prickleVersion};-split-package:=merge-first",
        s"microjson;version=${Dependencies.microJsonVersion};-split-package:=merge-first"
      )
    ),
  ) {

    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(

      OsgiKeys.embeddedJars := {
        (Compile / externalDependencyClasspath).value.map(_.data)
          .filter { f =>
            f.getName().startsWith("prickle") || f.getName().startsWith("microjson")
          }
      }
    )
  }

  override val project = helper.baseProject
}
