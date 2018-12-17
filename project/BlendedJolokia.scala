import sbt._
import blended.sbt.Dependencies

object BlendedJolokia extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.jolokia",
    description = "Provide an Actor based Jolokia Client to access JMX resources of a container via REST",
    deps = Seq(
      Dependencies.sprayJson,
      Dependencies.jsonLenses,
      Dependencies.slf4j,
      Dependencies.sttp,
      Dependencies.jolokiaJvmAgent % "runtime",
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      exportPackage = Seq(
        s"${b.bundleSymbolicName}"
      )
    )
  ) {
    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / Keys.javaOptions += {
        val jarFile = Keys.dependencyClasspathAsJars.in(Test).value
          .map(_.data).find(f => f.getName().startsWith("jolokia-jvm-")).get
        println(s"Using Jolokia agent from: $jarFile")
        s"-javaagent:$jarFile=port=7777,host=localhost"
      }
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedAkka.project,
    BlendedTestsupport.project % "test"
  )
}
