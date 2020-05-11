package blended.jmx

import java.lang.management.ManagementFactory

import blended.jmx.internal.BlendedMBeanServerFacadeImpl
import blended.jmx.json.PrickleProtocol._
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.RichTry._
import blended.util.logging.Logger
import org.scalatest.matchers.should.Matchers
import prickle._

class JmxAttributeCompanionSpec extends LoggingFreeSpec
  with Matchers {

  private val log : Logger = Logger[JmxAttributeCompanionSpec]
  private val mbf : BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(
    ManagementFactory.getPlatformMBeanServer()
  )

  "The JmxAttributeCompanion should" - {

    "translate Jmx objects into proper Json serializable objects" in {

      val names : List[JmxObjectName] = mbf.allMbeanNames().unwrap

      names should not be (empty)

      names.foreach { name =>
        log.info(s"testing with objName [$name]")
        val bean : JmxBeanInfo = mbf.mbeanInfo(name).unwrap
        val json : String = Pickle.intoString(bean)
        val obj : JmxBeanInfo = Unpickle[JmxBeanInfo].fromString(json).unwrap
        log.info(bean.toString())
        log.info(obj.toString())
        assert(bean === obj)
      }
    }
  }

}
