package blended.jmx

import java.lang.management.ManagementFactory

import blended.jmx.internal.BlendedMBeanServerFacadeImpl
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers
import blended.util.RichTry._
import prickle._
import blended.jmx.json.PrickleProtocol._

class JmxAttributeCompanionSpec extends LoggingFreeSpec
  with Matchers {

  private val mbf : BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(
    ManagementFactory.getPlatformMBeanServer()
  )

  "The JmxAttributeCompanion should" - {

    "translate Jmx objects into proper Json serializable objects" in {

      val names : List[JmxObjectName] = mbf.allMbeanNames().unwrap

      names should not be (empty)

      names.foreach { name =>
        val bean : JmxBeanInfo = mbf.mbeanInfo(name).unwrap
        val json : String = Pickle.intoString(bean)
        val obj : JmxBeanInfo = Unpickle[JmxBeanInfo].fromString(json).unwrap
        assert(bean === obj)
      }
    }
  }

}
