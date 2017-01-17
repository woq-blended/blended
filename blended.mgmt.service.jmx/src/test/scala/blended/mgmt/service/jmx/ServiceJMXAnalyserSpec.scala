package blended.mgmt.service.jmx

import java.lang.management.ManagementFactory

import blended.mgmt.service.jmx.internal.{ServiceJmxAnalyser, ServiceJmxConfig}
import com.typesafe.config.ConfigFactory
import org.scalatest.{FreeSpec, Matchers}

class ServiceJMXAnalyserSpec extends FreeSpec
  with Matchers {

  val server = ManagementFactory.getPlatformMBeanServer()
  val config = ServiceJmxConfig(ConfigFactory.load("javalang.conf"))

  val analyser = new ServiceJmxAnalyser(server, config)

  "The Service Analyser" - {

    "should find a specified MBean" in {

      val list = analyser.analyse()
      list should have size (1)

    }
  }
}
