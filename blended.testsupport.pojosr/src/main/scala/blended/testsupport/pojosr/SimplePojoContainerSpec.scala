package blended.testsupport.pojosr

import org.osgi.framework.BundleActivator
import org.scalatest.{BeforeAndAfterAll, FreeSpec}

abstract class SimplePojoContainerSpec
  extends FreeSpec
  with BeforeAndAfterAll { this: PojoSrTestHelper =>

  /**
   * Factory for bundles.
   * A `Seq` of bundle name and activator class.
   */
  def bundles: Seq[(String, BundleActivator)]

  def mandatoryPropertyNames: List[String] = List.empty

  override protected def afterAll(): Unit = {
    _registry.foreach { r =>
      stopRegistry(r)
    }
    // drop registry after stop
    _registry = None
    super.afterAll()
  }

  private[this] var _registry: Option[BlendedPojoRegistry] = None

  def registry: BlendedPojoRegistry = {
    _registry.getOrElse {
      _registry = Some(
        bundles.foldLeft(createSimpleBlendedContainer(mandatoryPropertyNames).get) {
          case (current, (name, activator)) =>
            startBundle(current)(name, activator).get._2
        }
      )
      _registry.get
    }
  }
}
