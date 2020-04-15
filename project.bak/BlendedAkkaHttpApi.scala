import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import com.typesafe.sbt.osgi.OsgiKeys._
import phoenix.ProjectFactory
import sbt.Keys._
import sbt._

object BlendedAkkaHttpApi extends ProjectFactory {
  //scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:off object.name
    override val projectName : String = "blended.akka.http.api"
    override val description : String = "Package the Akka Http API into a bundle."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.akkaHttp.intransitive(),
      Dependencies.akkaHttpCore.intransitive(),
      Dependencies.akkaParsing.intransitive()
    )

    override def bundle : OsgiBundle = super.bundle.copy(
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

