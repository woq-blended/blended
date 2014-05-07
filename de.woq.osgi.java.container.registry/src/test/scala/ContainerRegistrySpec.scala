import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.osgi.akka.system.{ExposedActor, InitializeBundle}
import de.woq.osgi.java.container.registry.ContainerInfo
import de.woq.osgi.java.container.registry.internal.{RegistryBundleName, ContainerRegistryImpl}
import de.woq.osgi.java.testsupport.TestActorSys
import org.mockito.ArgumentCaptor
import org.osgi.framework.BundleContext
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import de.woq.osgi.java.container.registry.ContainerRegistryProtocol._
import org.mockito.Mockito.verify
import de.woq.osgi.akka.modules._

/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class ContainerRegistrySpec extends WordSpec with MockitoSugar with Matchers {


  "Container registry" should {

    "Respond with a OK message upon an container update message" in new TestActorSys {
      implicit val osgiContext = mock[BundleContext]

      val registry = TestActorRef(Props(ContainerRegistryImpl()))

      registry ! UpdateContainerInfo(ContainerInfo("foo", Map("name" -> "andreas")))

      expectMsg(ContainerRegistryResponseOK("foo"))
    }

    "Register the bundle actor as a service" in new TestActorSys with RegistryBundleName {
      implicit val osgiContext = mock[BundleContext]
      val richContext : RichBundleContext = osgiContext

      val registry = TestActorRef(Props(ContainerRegistryImpl()))
      registry ! InitializeBundle(osgiContext)

      val ifaceCaptor = ArgumentCaptor.forClass(classOf[Array[String]])
      val propertiesCaptor = ArgumentCaptor.forClass(classOf[java.util.Dictionary[String, Any]])
      val svcCaptor = ArgumentCaptor.forClass(classOf[AnyRef])

      verify(osgiContext).registerService(
        ifaceCaptor.capture(),
        svcCaptor.capture(),
        propertiesCaptor.capture()
      )

      ifaceCaptor.getValue should be(Array(classOf[ExposedActor].getName))

      propertiesCaptor.getValue.size should be (1)
      propertiesCaptor.getValue get "bundle" should be (bundleSymbolicName)

    }
  }

}
