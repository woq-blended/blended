package blended.jmx

import java.lang.management.ManagementFactory

import blended.jmx.JmxObjectNameCompanion._
import blended.jmx.internal.BlendedMBeanServerFacadeImpl
import blended.util.RichTry._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class MbeanServerFacadeSpec extends AnyFreeSpec
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
      JmxObjectNameCompanion.createJmxObjectName(names.head).unwrap should be (objName)
    }

    "allow to query for a group of names" in {
      val objName : JmxObjectName = JmxObjectName("java.lang:type=MemoryPool").unwrap
      val names : List[JmxObjectName] = mbf.mbeanNames(Some(objName)).unwrap

      assert(names.size > 1)
      assert(names.forall { n =>
        objName.isAncestor(JmxObjectNameCompanion.createJmxObjectName(n).unwrap)
      })

    }
  }
}
