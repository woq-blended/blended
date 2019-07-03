package blended.jmx

import java.lang.management.ManagementFactory

import blended.jmx.internal.BlendedMBeanServerFacadeImpl
import blended.util.RichTry._
import org.scalatest.{FreeSpec, Matchers}

class MbeanServerFacadeSpec extends FreeSpec
  with Matchers {

  private val mbf : BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(
    ManagementFactory.getPlatformMBeanServer()
  )

  "The MBeanServerFacade should" - {

    "allow to query all the MBeans" in {
      val names : List[JmxObjectName] = mbf.allMbeanNames().unwrap

      names should not be empty
    }

    "allow to query a specific name" in {
      val objName : JmxObjectName = JmxObjectName("java.lang:type=Memory").unwrap
      val names : List[JmxObjectName] =
        mbf.mbeanNames(Some(objName)).unwrap

      names should have size 1
      JmxObjectName.fromObjName(names.head.objectName) should be (objName)
    }

    "allow to query for a group of names" in {
      val objName : JmxObjectName = JmxObjectName("java.lang:type=MemoryPool").unwrap
      val names : List[JmxObjectName] =
        mbf.mbeanNames(Some(objName)).unwrap

      assert(names.size > 1)
      assert(names.forall { n =>
        objName.isAncestor(JmxObjectName.fromObjName(n.objectName))
      })

    }
  }
}
