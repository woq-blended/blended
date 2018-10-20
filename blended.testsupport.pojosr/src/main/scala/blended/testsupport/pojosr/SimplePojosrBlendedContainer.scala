package blended.testsupport.pojosr

import blended.container.context.api.ContainerIdentifierService
import blended.container.context.impl.internal.ContainerIdentifierServiceImpl
import domino.DominoActivator
import org.osgi.framework.BundleActivator

// TODO: Refactor, so that BundleActivator factory is not exposed to test
// code (special treatment only for the id Service within the trait, so that 
// a base directory for running a test case can be set).
trait SimplePojosrBlendedContainer { this: PojoSrTestHelper =>

  def uuid : String = "simple"

  private[this] def idSvcActivator(
    baseDir: String,
    mandatoryProperties: Option[String] = None
  ): BundleActivator = {
    new DominoActivator {
      mandatoryProperties.foreach(s =>
        System.setProperty("blended.updater.profile.properties.keys", s))

      whenBundleActive {
        val ctCtxt = new MockContainerContext(baseDir)
        // This needs to be a fixed uuid as some tests might be for restarts and require the same id
        new ContainerIdentifierServiceImpl(ctCtxt) {
          override lazy val uuid: String = uuid
        }.providesService[ContainerIdentifierService]
      }
    }
  }

  def withSimpleBlendedContainer[T](
    baseDir: String,
    mandatoryProperties: List[String] = List.empty
  )(f: BlendedPojoRegistry => T): T = {

    System.setProperty("BLENDED_HOME", baseDir)
    System.setProperty("blended.home", baseDir)
    System.setProperty("blended.container.home", baseDir)

    withPojoServiceRegistry[T] { sr =>
      withStartedBundle[T](sr)(
        classOf[ContainerIdentifierServiceImpl].getPackage().getName(),
        Some(() => idSvcActivator(baseDir, Some(mandatoryProperties.mkString(","))))
      )(f)
    }
  }
}
