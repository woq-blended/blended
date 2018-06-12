import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../../blended.build/build-versions.scala
//#include ../../blended.build/build-dependencies.scala
//#include ../../blended.build/build-plugins.scala
//#include ../../blended.build/build-common.scala

BlendedModel(
  gav = Blended.dockerDemoApacheDS,
  packaging = "jar",
  description = """A simple docker container with providing an Apache Directory Service LDAP service.""",
  plugins = Seq(
    dockerMavenPlugin,
    Plugin(
      Plugins.download,
      executions = Seq(
        Execution(
          id = "download-apacheds",
          goals = Seq("wget"),
	  phase = "generate-resources",
          configuration = Config(
            url = "http://www-eu.apache.org/dist//directory/apacheds/dist/2.0.0-M24/apacheds-2.0.0-M24-x86_64.rpm",
            outputDirectory = "${project.build.directory}/docker/apacheds",
            md5 = "8a8b4829908ff74ac63c97c7be34ba09"
          )
        )
      )
    )
  )
)
