package blended.jmx.internal

import java.lang.management.ManagementFactory

import blended.jmx.internal.OpenMBeanMapperImpl
import blended.jmx.OpenMBeanExporter._
import blended.testsupport.scalatest.LoggingFreeSpec
import javax.management.{InstanceAlreadyExistsException, MBeanServer}
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

class OpenMBeanExporterSpec extends LoggingFreeSpec with PropertyChecks with Matchers {

  "The OpenMBeanExporterImp should" - {

    val mapper = new OpenMBeanMapperImpl()
    val server = ManagementFactory.getPlatformMBeanServer()
    val exporter = new OpenMBeanExporterImpl(mapper) {
      override def mbeanServer: MBeanServer = server
    }

    case class Export(name: String, hobbies: Seq[String])

    "properly export a case class" in {
      val export1 = Export("Me", Seq("cycling", "hiking"))
      val objectName = exporter.objectName(export1)
      assert(server.isRegistered(objectName) === false)
      try {
        assert(server.isRegistered(objectName) === false)
        exporter.export(export1)
        assert(server.isRegistered(objectName) === true)
      } finally {
        server.unregisterMBean(objectName)
      }
    }


    "properly re-export a case class" in {
      val export1 = Export("Me", Seq("cycling", "hiking"))
      val export2 = Export("You", Seq("cycling", "hiking"))
      val objectName = exporter.objectName(export1)
      assert(server.isRegistered(objectName) === false)
      try {
        assert(server.isRegistered(objectName) === false)
        exporter.export(export1, true)
        assert(server.isRegistered(objectName) === true)
        assert(server.getAttribute(objectName, "name") === "Me")
        exporter.export(export2, true)
        assert(server.isRegistered(objectName) === true)
        assert(server.getAttribute(objectName, "name") === "You")
      } finally {
        server.unregisterMBean(objectName)
      }
    }

    "fails when re-exporting case class with replaceExiting=false" in {
      val export1 = Export("Me", Seq("cycling", "hiking"))
      val export2 = Export("You", Seq("cycling", "hiking"))
      val objectName = exporter.objectName(export1)
      assert(server.isRegistered(objectName) === false)
      try {
        assert(server.isRegistered(objectName) === false)
        exporter.export(export1, false)
        assert(server.isRegistered(objectName) === true)
        intercept[InstanceAlreadyExistsException]{
          exporter.export(export2, false).get
        }
      } finally {
        server.unregisterMBean(objectName)
      }
    }

  }

}
