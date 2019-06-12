package blended.jmx

import java.lang.management.ManagementFactory

import blended.jmx.internal.BlendedMBeanServerFacadeImpl
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import javax.management.{MBeanServer, ObjectName}
import org.scalatest.Matchers

class JmxObjectNameSpec extends LoggingFreeSpec
  with Matchers {

  private val log : Logger = Logger[JmxObjectNameSpec]
  private val mBeanServer : MBeanServer = ManagementFactory.getPlatformMBeanServer()

  "The JmxObjectName should" - {

    "be creatable from a JMX Object Name" in {
      val objName : ObjectName= new ObjectName("blended:type=ConnectionFactory,name=foo")
      val jmxObjName = JmxObjectNameCompanion.createJmxObjectName(objName).get

      jmxObjName.domain should be ("blended")
      jmxObjName.properties should have size 2
      jmxObjName.properties should contain ("type" ->"ConnectionFactory")
      jmxObjName.properties should contain ("name" -> "foo")
    }

    "be creatable from a well formatted String" in {
      val mbf : BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(mBeanServer)
      val names : List[JmxObjectName] = mbf.mbeanNames().get

      assert(
        names.forall{ n =>
          mbf.mbeanInfo(n)
          JmxObjectName(n.objectName).get.equals(n)
        }
      )
    }
  }
}
