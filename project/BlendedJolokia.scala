import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._
import sbt.Keys._

object BlendedJolokia extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name

    override val projectName : String = "blended.jolokia"
    override val description : String = "Provide an Actor based Jolokia Client to access JMX resources of a container via REST"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.sprayJson,
      Dependencies.jsonLenses,
      Dependencies.slf4j,
      Dependencies.sttp,
      Dependencies.jolokiaJvmAgent % "runtime",
      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      exportPackage = Seq(
        projectName
      )
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / javaOptions += {
        val jarFile = Keys.dependencyClasspathAsJars.in(Test).value
          .map(_.data).find(f => f.getName().startsWith("jolokia-jvm-")).get
        streams.value.log.info(s"Using Jolokia agent from: $jarFile")
        s"-javaagent:$jarFile=port=7777,host=localhost"
      }
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedTestsupport.project % Test
    )
  }
}
