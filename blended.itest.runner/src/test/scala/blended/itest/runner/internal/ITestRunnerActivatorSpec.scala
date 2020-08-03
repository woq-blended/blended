package blended.itest.runner

import org.scalatest.matchers.must.Matchers
import blended.testsupport.pojosr.SimplePojoContainerSpec
import blended.testsupport.pojosr.PojoSrTestHelper
import blended.testsupport.BlendedTestSupport

import java.io.File
import blended.akka.internal.BlendedAkkaActivator
import org.osgi.framework.BundleActivator
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.itest.runner.internal.ITestRunnerActivator

class ITestRunnerActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper 
  with Matchers {
  
  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.itest.runner" -> new ITestRunnerActivator()
  )

  "The ITest Runner activator should" - {

    "simply start" in logException {
    }
  } 
}
