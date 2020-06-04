package blended.mgmt.base.internal

import blended.container.context.api.ContainerContext
import blended.testsupport.scalatest.LoggingFreeSpec
import com.typesafe.config.{Config, ConfigFactory}
import de.tobiasroeser.lambdatest.proxy.TestProxy
import org.osgi.framework.{Bundle, BundleContext}

class FrameworkServiceSpec extends LoggingFreeSpec {

  "The FrameworkService" - {
    "restartContainer should update bundle 0" in {
      var bundleZeroUpdated = false
      class MockFrameworkBundle(val id: Long) {
        def update(): Unit = {
          id match {
            case 0 => bundleZeroUpdated = true
            case x => fail(s"Unexpected bundle update for id ${id}")
          }
        }
      }
      class MockBundleContext() {
        def getBundle(id: Long): Bundle = TestProxy.proxy(classOf[Bundle], new MockFrameworkBundle(id))
      }
      val bundleContextMock: BundleContext = TestProxy.proxy(classOf[BundleContext], new MockBundleContext())

      class MockContainerContext() {
        def containerConfig(): Config = ConfigFactory.empty()
      }
      val containerContext: ContainerContext = TestProxy.proxy(classOf[ContainerContext], new MockContainerContext())

      val frameworkService = new FrameworkService(bundleContextMock, containerContext)
      frameworkService.restartContainer("test", false)

      assert(bundleZeroUpdated === true)
    }
  }

}
