package blended.container.registry.internal

import blended.akka.OSGIActorConfig
import blended.container.context.{ContainerContext, ContainerIdentifierService}
import blended.testsupport.TestActorSys
import blended.updater.config.{ContainerInfo, ContainerRegistryResponseOK, UpdateContainerInfo}
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito.when
import org.osgi.framework.{Bundle, BundleContext}
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.mock.MockitoSugar

class ContainerRegistrySpec extends WordSpec with MockitoSugar with Matchers {

  val osgiContext = mock[BundleContext]
  val idSvc = mock[ContainerIdentifierService]
  val ctContext = mock[ContainerContext]
  val bundle = mock[Bundle]

  when(idSvc.containerContext) thenReturn(ctContext)
  when(ctContext.getProfileConfigDirectory()) thenReturn ("./target/test-classes")
  when(osgiContext.getBundle()) thenReturn(bundle)
  when(bundle.getBundleContext) thenReturn(osgiContext)
  when(bundle.getSymbolicName) thenReturn("foo")

  "Container registry" should {

    "Respond with a OK message upon an container update message" in TestActorSys { testkit =>

      implicit val system = testkit.system

      val probe = TestProbe()

      val registry = TestActorRef(ContainerRegistryImpl.props(OSGIActorConfig(osgiContext, system, ConfigFactory.empty(), idSvc)))
      val profiles = List()
      registry.tell(UpdateContainerInfo(ContainerInfo("foo", Map("name" -> "andreas"), serviceInfos = List(), profiles, System.currentTimeMillis())), probe.ref)

      probe.expectMsg(ContainerRegistryResponseOK("foo"))
    }
  }

}
