package blended.testsupport.pojosr

import org.osgi.framework.BundleActivator
import org.scalatest.{BeforeAndAfterAll, FreeSpec}

abstract class SimplePojoContainerSpec extends FreeSpec
  with BeforeAndAfterAll { this : PojoSrTestHelper =>

  def bundles : Seq[(String, BundleActivator)]
  def mandatoryPropertyNames : List[String] = List.empty

  override protected def afterAll(): Unit = {
    stopRegistry(registry)
  }

  lazy val registry : BlendedPojoRegistry = {
    bundles.foldLeft(createSimpleBlendedContainer(mandatoryPropertyNames).get){ case (current, (name, activator)) =>
      startBundle(current)(name, activator).get._2
    }
  }
}
