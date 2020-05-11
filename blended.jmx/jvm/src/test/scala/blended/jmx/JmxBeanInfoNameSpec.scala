package blended.jmx

import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.RichTry._
import javax.management.ObjectName
import org.scalatest.matchers.should.Matchers

class JmxBeanInfoNameSpec extends LoggingFreeSpec
  with Matchers {

  "The JmxObjectName should" - {

    "be creatable from a JMX Object Name" in {
      val objName : ObjectName= new ObjectName("blended:type=ConnectionFactory,name=foo")
      val jmxObjName = JmxObjectNameCompanion.createJmxObjectName(objName).unwrap

      jmxObjName.domain should be ("blended")
      jmxObjName.properties should have size 2
      jmxObjName.properties should contain ("type" ->"ConnectionFactory")
      jmxObjName.properties should contain ("name" -> "foo")
    }

    "be creatable from a well formatted String" in {

      val jmxObjName : JmxObjectName = JmxObjectName("blended:type=ConnectionFactory,name=foo").unwrap

      jmxObjName.domain should be ("blended")
      jmxObjName.properties should have size 2
      jmxObjName.properties should contain ("type" ->"ConnectionFactory")
      jmxObjName.properties should contain ("name" -> "foo")

      intercept[InvalidObjectNameFormatException] {
        JmxObjectName("blended:type=,name=foo").unwrap
      }

      intercept[InvalidObjectNameFormatException] {
        JmxObjectName("blended:").unwrap
      }

      // scalastyle::off null
      intercept[InvalidObjectNameFormatException] {
        JmxObjectName(null).unwrap
      }
      // scalastyle::on null

      intercept[InvalidObjectNameFormatException] {
        JmxObjectName("").unwrap
      }
    }
  }
}
