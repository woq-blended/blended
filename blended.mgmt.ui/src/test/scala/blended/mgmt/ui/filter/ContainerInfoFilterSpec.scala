package blended.mgmt.ui.filter

import org.scalatest.FreeSpec
import blended.mgmt.ui.components.filter.ContainerInfoFilter
import blended.updater.config.ContainerInfo
import org.scalatest.Matchers

class ContainerInfoFilterSpec extends FreeSpec with Matchers {

  "ContainerId matcher" - {
    "should match containerId" in {
      val ci = ContainerInfo(containerId = "123456", Map(), List(), List())
      ContainerInfoFilter.ContainerId("123456").matches(ci) should equal(true)
    }
  }

  "FreeText Matcher" - {
    "should match exact containerId" in {
      val ci = ContainerInfo(containerId = "123456", Map(), List(), List())
      ContainerInfoFilter.FreeText("123456").matches(ci) should equal(true)

    }
  }

}