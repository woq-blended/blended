package blended.akka.internal

import java.io.File

import akka.actor.ActorSystem
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._

class AkkaActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    ("blended.akka" -> new BlendedAkkaActivator())
  )

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  "The Akka activator" - {

    "should register a container wide Actor system as a service" in {

      implicit val timeout : FiniteDuration = 3.seconds
      mandatoryService[ActorSystem](registry)(None)

    }
  }

}
