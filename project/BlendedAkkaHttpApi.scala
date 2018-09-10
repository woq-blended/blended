import com.typesafe.sbt.osgi.OsgiKeys
import sbt.Keys._
import sbt._

object BlendedAkkaHttpApi extends ProjectHelper {

  private[this] val helper: ProjectSettings = new ProjectSettings(
    projectName = "blended.akka.http.api",
    description = "Package the complete Spray API into a bundle.",
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
      // OsgiKeys.embeddedJars := dependencyClasspath.in(Compile).value.files
      OsgiKeys.embeddedJars := {
        (Keys.externalDependencyClasspath in Compile).value
          .map(_.data)
          .filter(f =>
            f.getName().contains("akka-parsing_") ||
              f.getName().contains("akka-http-core_") ||
              f.getName().contains("akka-http_")
          )
      }

      //        OsgiKeys.embeddedJars := {
      //      Seq(
      //        BuildHelper.resolveModuleFile(Dependencies.akkaHttp.intransitive(), target.value),
      //        BuildHelper.resolveModuleFile(Dependencies.akkaHttpCore.intransitive(), target.value),
      //        BuildHelper.resolveModuleFile(Dependencies.akkaParsing.intransitive(), target.value)
      //      ).flatten.distinct

    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedUtilLogging.project
  )
}

