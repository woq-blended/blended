import sbt._

object BlendedJolokia extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.jolokia",
    description = "Provide an Actor based Jolokia Client to access JMX resources of a container via REST",
    deps = Seq(
      Dependencies.sprayJson,
      Dependencies.jsonLenses,
      Dependencies.slf4j,
      Dependencies.akkaHttp,
      Dependencies.akkaStream,
      Dependencies.akkaSlf4j % "test",
      Dependencies.jolokiaJvmAgent % "runtime",
      Dependencies.scalatest % "test",
      Dependencies.mockitoAll % "test",
      Dependencies.slf4jLog4j12 % "test"
    ),
    adaptBundle = b => b.copy(
      exportPackage = Seq(
        s"${b.bundleSymbolicName}",
        s"${b.bundleSymbolicName}.model",
        s"${b.bundleSymbolicName}.protocol"
      )
    )
  ) {
    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / Keys.javaOptions += {
        val jarFile = Keys.dependencyClasspathAsJars.in(Test).value
          .map(_.data).find(f => f.getName().startsWith("jolokia-jvm-")).get
        println(s"Using Jolokia agent from: ${jarFile}")
        s"-javaagent:${jarFile}=port=7777,host=localhost"
      }
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedAkka.project,
    BlendedTestsupport.project % "test"
  )
}
