package blended.itestsupport.docker

import java.util.UUID

import akka.actor.{ActorSystem, Terminated}
import akka.testkit.{TestActorRef, TestProbe}
import blended.itestsupport.docker.protocol._
import blended.itestsupport.{ContainerLink, ContainerUnderTest}
import blended.testsupport.TestActorSys
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

class DependentContainerActorSpec extends AnyWordSpec
  with DockerTestSetup
  with MockitoSugar {

  "the DependentContainerActor" should {

    "Respond with a DependenciesStarted message after the last dependant container was started" in TestActorSys { testkit =>
      implicit val system : ActorSystem = testkit.system
      val probe = TestProbe()

      val cut = ContainerUnderTest(
        ctName = "foo",
        imgPattern = "^atooni/bar:latest",
        dockerName = "foobar",
        imgId = UUID.randomUUID().toString,
        links = List(ContainerLink("blended_demo_0", "blended_demo"))
      )

      val depActor = TestActorRef(DependentContainerActor.props(cut))

      probe.watch(depActor)

      depActor.tell(ContainerStarted(Right(cut.copy(ctName = "blended_demo_0"))), probe.ref)
      probe.expectMsg( DependenciesStarted(Right(cut.copy(links = List(ContainerLink("foobar", "blended_demo"))))) )

      probe.fishForMessage() {
        case _ : Terminated => true
        case _ => false
      }
    }

  }

}
