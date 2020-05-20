package blended.testsupport.pojosr

import org.osgi.framework.{Bundle, BundleActivator}
import org.scalatest.{BeforeAndAfterAll, TestSuite}

abstract class SimplePojoContainerSpec
  extends TestSuite
  with BeforeAndAfterAll { this : PojoSrTestHelper =>

  private var _registry : Option[BlendedPojoRegistry] = None

  def registry : BlendedPojoRegistry = _registry match {
    case Some(r) => r
    case None => throw new Exception("Pojo Registry not yet defined")
  }

  /**
   * Specify, which properties are mandatory for the simulated container.
   */
  def mandatoryPropertyNames : List[String] = List.empty

  /**
   * If required, inject additional system properties when firing up the container.
   */
  def systemProperties : Map[String, String] = Map.empty

  def bundleByName(r : BlendedPojoRegistry)(name : String) : Option[Bundle] = r.getBundleContext().getBundles().find {
    b => b.getSymbolicName() == name
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val reg = createSimpleBlendedContainer(mandatoryPropertyNames, systemProperties).get
    bundles.foldLeft(reg) {
      case (current, (name, activator)) => startBundle(current)(name, activator).get._2
    }
   _registry = Some(reg)
  }

  override protected def afterAll() : Unit = {
    _registry.foreach(stopRegistry)
    super.afterAll()
  }
}
