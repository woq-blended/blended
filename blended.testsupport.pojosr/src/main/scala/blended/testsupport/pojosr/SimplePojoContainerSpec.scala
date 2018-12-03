package blended.testsupport.pojosr

import org.osgi.framework.BundleActivator
import org.scalatest.{BeforeAndAfterAll, TestSuite}

abstract class SimplePojoContainerSpec
  extends TestSuite
  with BeforeAndAfterAll { this: PojoSrTestHelper =>

  /**
   * Factory for bundles.
   * A `Seq` of bundle name and activator class.
   */
  def bundles: Seq[(String, BundleActivator)]

  /**
    * Specify, which properties are mandatory for the simulated container.
    */
  def mandatoryPropertyNames: List[String] = List.empty

  /**
    * If required, inject additional system properties when firing up the container.
    */
  def systemProperties : Map[String, String] = Map.empty

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
        bundles.foldLeft(createSimpleBlendedContainer(mandatoryPropertyNames, systemProperties).get) {
          case (current, (name, activator)) =>
            startBundle(current)(name, activator).get._2
        }
      )
      _registry.get
    }
  }
}
