package blended.mgmt.base.internal

import java.io.File

import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers
import scala.concurrent.duration._

class MgmtBaseActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.mgmt.base" -> new MgmtBaseActivator()
  )

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  "The Mgmt base activator" - {

    "should register the framework as OSGi service" in {

      implicit val timeout : FiniteDuration = 3.seconds
      mandatoryService[blended.mgmt.base.FrameworkService](registry)(None)

    }
  }
}
