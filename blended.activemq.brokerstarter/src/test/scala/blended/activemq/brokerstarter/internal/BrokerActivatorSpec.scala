package blended.activemq.brokerstarter.internal

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.{Connected, ConnectionStateChanged}
import blended.security.internal.SecurityActivator
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._

@RequiresForkedJVM
class BrokerActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.security" -> new SecurityActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

  private implicit val timeout : FiniteDuration = 5.seconds
  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)

  "The BrokerActivator should" - {

    "start the configured brokers correctly" in {

      var connected : List[String] = List.empty

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      1.to(2).foreach{ _ =>
        probe.fishForMessage(timeout){
          case s : ConnectionStateChanged if s.state.status == Connected =>
            connected = (s.state.provider :: connected).distinct
            true
          case _ => false
        }
      }

      connected should have size 2
    }
  }
}
