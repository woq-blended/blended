package blended.jolokia

import java.net.{InetAddress, InetSocketAddress}

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.JavaConverters._

import scala.concurrent.duration._

class JolokiaClientSpec extends AnyWordSpec
  with Matchers {

  val good = {
    val url = System.getProperty("jolokia.agent")
    new JolokiaClient(JolokiaAddress(jolokiaUrl = url))
  }

  val bad = {
    // Choose a port that is currently not in use
    val freePort = new InetSocketAddress(0).getPort()
    new JolokiaClient(JolokiaAddress(
      jolokiaUrl = s"http://localhost:${freePort}/jolokia"
    ))
  }

  "The Jolokia client" should {

    "Connect to Jolokia" in {
      good.version.get
    }

    "Allow to search for MBeans by domain only" in {
      val result : JolokiaSearchResult = good.search(MBeanSearchDef(
        jmxDomain = "java.lang"
      )).get

      assert(result.mbeanNames.nonEmpty)
    }

    "Allow to search for MBeans by domain and properties" in {

      val result : JolokiaSearchResult = good.search(MBeanSearchDef(
        jmxDomain = "java.lang",
        searchProperties = Map("type" -> "Memory")
      )).get

      assert(result.mbeanNames.nonEmpty)
    }

    "Allow to read a specific MBean" in {
      good.read("java.lang:type=Memory").get
    }

    "Allow to execute a given operation on a MBean" in {

      val result : JolokiaExecResult = good.exec(OperationExecDef(
        objectName = "java.lang:type=Threading",
        operationName = "dumpAllThreads(boolean,boolean)",
        parameters = List("true", "true")
      )).get

      assert(result.objectName == "java.lang:type=Threading")
      assert(result.operationName == "dumpAllThreads(boolean,boolean)")
    }

    "Respond with a failure if the rest call fails" in {

      intercept[Exception] {
        bad.version.get
      }
    }
  }
}
