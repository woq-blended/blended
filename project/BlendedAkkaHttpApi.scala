import sbt._
import sbt.Keys._
import com.typesafe.sbt.osgi.OsgiKeys._

object BlendedAkkaHttpApi extends ProjectHelper {

  private[this] val helper: ProjectSettings = new ProjectSettings(
    projectName = "blended.akka.http.api",
    description = "Package the Akka Http API into a bundle.",
    deps = Seq(
      Dependencies.akkaHttp.intransitive(),
      Dependencies.akkaHttpCore.intransitive(),
      Dependencies.akkaParsing.intransitive()
    ),
    adaptBundle = b => b.copy(
      importPackage = Seq(
        "com.sun.*;resolution:=optional",
        "sun.*;resolution:=optional",
        "net.liftweb.*;resolution:=optional",
        "play.*;resolution:=optional",
        "twirl.*;resolution:=optional",
        "org.json4s.*;resolution:=optional"
      ),
      exportContents = Seq(
        s"akka.http.*;version=${Dependencies.akkaHttpVersion};-split-package:=merge-first"
      )
    )
  ) {

    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(

      embeddedJars := {
        (Compile/externalDependencyClasspath).value
          .map(_.data)
          .filter(f =>
            f.getName().contains("akka-parsing_") ||
            f.getName().contains("akka-http-core_") ||
            f.getName().contains("akka-http_")
          )
      }
    )
  }

  override val project = helper.baseProject
}

