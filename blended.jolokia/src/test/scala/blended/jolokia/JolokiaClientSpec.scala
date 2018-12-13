package blended.jolokia

import blended.jolokia.model._
import blended.jolokia.protocol._
import org.scalatest.{Matchers, WordSpec}

class JolokiaClientSpec extends WordSpec
  with Matchers {

  val good = new JolokiaClient(JolokiaAddress(
    jolokiaUrl = "http://localhost:7777/jolokia"
  ))

  val bad = new JolokiaClient(JolokiaAddress(
    jolokiaUrl = "http://localhost:43888/jolokia"
  ))

  "The Jolokia client" should {

    "Connect to Jolokia" in {
      good.version.get
    }

    "Allow to search for MBeans by domain only" in {
      val result : JolokiaSearchResult = good.search(new MBeanSearchDef {
        override def jmxDomain: String = "java.lang"
      }).get

      assert(result.mbeanNames.nonEmpty)
    }

    "Allow to search for MBeans by domain and properties" in {

      val result : JolokiaSearchResult = good.search(new MBeanSearchDef {
        override def jmxDomain: String = "java.lang"
        override def searchProperties : Map[String, String] = Map( "type" -> "Memory" )
      }).get

      assert(result.mbeanNames.nonEmpty)
    }

    "Allow to read a specific MBean" in {
      good.read("java.lang:type=Memory").get
    }

    "Allow to execute a given operation on a MBean" in {

      val result : JolokiaExecResult = good.exec(new OperationExecDef {
        override def objectName : String = "java.lang:type=Threading"
        override def operationName: String = "dumpAllThreads"
        override def parameters : List[String] = List("true", "true")
      }).get

      assert(result.objectName == "java.lang:type=Threading" )
      assert(result.operationName == "dumpAllThreads" )
    }
    
    "Respond with a failure if the rest call fails" in {

      intercept[Exception]{
        bad.version.get
      }
    }
  }
}
