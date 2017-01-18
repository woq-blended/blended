package blended.mgmt.ui.filter

import org.scalatest.FreeSpec
import blended.mgmt.ui.components.filter.ContainerInfoFilter
import blended.updater.config.ContainerInfo
import org.scalatest.Matchers
import blended.updater.config.Profile

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
    "should match partial containerId" in {
      val ci = ContainerInfo(containerId = "123456", Map(), List(), List())
      ContainerInfoFilter.FreeText("2345").matches(ci) should equal(true)
    }
    "should match partial containerId prefix" in {
      val ci = ContainerInfo(containerId = "123456", Map(), List(), List())
      ContainerInfoFilter.FreeText("123").matches(ci) should equal(true)
    }
    "should match partial containerId suffix" in {
      val ci = ContainerInfo(containerId = "123456", Map(), List(), List())
      ContainerInfoFilter.FreeText("456").matches(ci) should equal(true)
    }
    "should not match different containerId" in {
      val ci = ContainerInfo(containerId = "123456", Map(), List(), List())
      ContainerInfoFilter.FreeText("234567").matches(ci) should equal(false)
    }
    "should match by property value" in {
      val ci = ContainerInfo(containerId = "123456", Map("key1" -> "value1"), List(), List())
      ContainerInfoFilter.FreeText("value1").matches(ci) should equal(true)
    }
    "should not match by missing property value" in {
      val ci = ContainerInfo(containerId = "123456", Map(), List(), List())
      ContainerInfoFilter.FreeText("value1").matches(ci) should equal(false)
    }
    "should match by profile name" in {
      val ci = ContainerInfo(containerId = "123456", Map(), List(), List(Profile("p1", "3.2.1", List())))
      ContainerInfoFilter.FreeText("p1").matches(ci) should equal(true)
    }
    "should match by profile name and version" in {
      val ci = ContainerInfo(containerId = "123456", Map(), List(), List(Profile("p1", "3.2.1", List())))
      ContainerInfoFilter.FreeText("p1-3.2.1").matches(ci) should equal(true)
    }
  }

}