import blended.sbt.Dependencies
import com.typesafe.sbt.osgi.OsgiKeys._
import phoenix.ProjectFactory
import sbt.Keys._
import sbt._

object BlendedAkkaHttpApi extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.akka.http.api"
    override val description = "Package the Akka Http API into a bundle."

    override def deps = Seq(
      Dependencies.akkaHttp.intransitive(),
      Dependencies.akkaHttpCore.intransitive(),
      Dependencies.akkaParsing.intransitive()
    )

    override def bundle = super.bundle.copy(
      importPackage = Seq(
        "com.sun.*;resolution:=optional",
        "sun.*;resolution:=optional",
        "net.liftweb.*;resolution:=optional",
        "play.*;resolution:=optional",
        "twirl.*;resolution:=optional",
        "org.json4s.*;resolution:=optional",
        "*"
      ),
      exportContents = Seq(
        s"akka.http.*;version=${Dependencies.akkaHttpVersion};-split-package:=merge-first"
      ),
      defaultImports = false
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      embeddedJars := {
        (Compile / externalDependencyClasspath).value
          .map(_.data)
          .filter(f =>
            f.getName().contains("akka-parsing_") ||
              f.getName().contains("akka-http-core_") ||
              f.getName().contains("akka-http_"))
      }
    )
  }
}

