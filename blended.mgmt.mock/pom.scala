import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.mgmtMock,
  packaging = "jar",
  description = "Mock server to simulate a larger network of blended containers for UI testing.",
  dependencies = Seq(
    Blended.mgmtBase,
    Blended.mgmtAgent,
    Blended.utilLogging,
    scalaLib,
    slf4j,
    slf4jLog4j12,
    prickle,
    wiremockStandalone,
    Deps.cmdOption,
    Deps.akkaActor
  ),
  properties = Map(
    "bundle.symbolicName" -> "${project.artifactId}",
    "bundle.namespace" -> "${project.artifactId}"
  ),
  plugins = Seq(
    scalaCompilerPlugin,
    Plugin(
      gav = Plugins.exec,
      executions = Seq(
        // To run the mock server, exec: mvn exec:java@mock-server
        Execution(
          id = "mock-server",
          goals = Seq("java"),
          phase = "none",
          configuration = Config(
            mainClass = "blended.mgmt.mock.server.MgmtMockServer"
          )
        ),
        // To run the mock server, exec: mvn exec:java@mock-clients
        Execution(
          id = "mock-clients",
          goals = Seq("java"),
          phase = "none",
          configuration = Config(
            mainClass = "blended.mgmt.mock.clients.MgmtMockClients"
          )
        )
      )
    )
  )
)
