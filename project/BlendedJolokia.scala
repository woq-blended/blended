import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedJolokia extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.jolokia"
    override val description = "Provide an Actor based Jolokia Client to access JMX resources of a container via REST"

    override def deps = Seq(
      Dependencies.sprayJson,
      Dependencies.jsonLenses,
      Dependencies.slf4j,
      Dependencies.sttp,
      Dependencies.jolokiaJvmAgent % "runtime",
      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle = super.bundle.copy(
      exportPackage = Seq(
        projectName
      )
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / Keys.javaOptions += {
        val jarFile = Keys.dependencyClasspathAsJars.in(Test).value
          .map(_.data).find(f => f.getName().startsWith("jolokia-jvm-")).get
        println(s"Using Jolokia agent from: $jarFile")
        s"-javaagent:$jarFile=port=7777,host=localhost"
      }
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedTestsupport.project % Test
    )
  }
}
