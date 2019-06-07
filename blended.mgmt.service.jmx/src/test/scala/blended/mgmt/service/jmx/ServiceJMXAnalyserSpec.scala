package blended.mgmt.service.jmx

import java.lang.management.ManagementFactory

import blended.mgmt.service.jmx.internal.{ServiceJmxAnalyser, ServiceJmxConfig}
import com.typesafe.config.ConfigFactory
import javax.management.MBeanServer
import org.scalatest.{FreeSpec, Matchers}

class ServiceJMXAnalyserSpec extends FreeSpec
  with Matchers {

  private val server : MBeanServer = ManagementFactory.getPlatformMBeanServer()
  val config : ServiceJmxConfig = ServiceJmxConfig(ConfigFactory.load("javalang.conf"))

  val analyser = new ServiceJmxAnalyser(server, config)

  "The Service Analyser" - {

    "should find a specified MBean" in {

      val list = analyser.analyse()
      list should have size 2

      val info = list.filter(_.name.contains("OperatingSystem")).head

      info.name should be("java.lang:type=OperatingSystem")
      info.serviceType should be("Runtime")
      //scalastyle:off magic.number
      info.lifetimeMsec should be(3000)

      info.props should have size 1
      info.props should contain key "Name"

      val memory = list.filter(_.name.contains("Memory")).head
      memory.serviceType should be("Runtime")
      memory.lifetimeMsec should be(3000)
      //scalastyle:on magic.number

      memory.props should have size 4
      memory.props should contain key "HeapMemoryUsage.used"
      memory.props should contain key "HeapMemoryUsage.max"
      memory.props should contain key "HeapMemoryUsage.committed"
      memory.props should contain key "HeapMemoryUsage.init"

    }
  }
}
