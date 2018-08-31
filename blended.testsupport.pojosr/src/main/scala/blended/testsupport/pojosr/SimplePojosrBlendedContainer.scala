package blended.testsupport.pojosr

import blended.container.context.api.ContainerIdentifierService
import blended.container.context.impl.internal.ContainerIdentifierServiceImpl
import domino.DominoActivator
import org.osgi.framework.BundleActivator

trait SimplePojosrBlendedContainer { this: PojoSrTestHelper =>

  private[this] def idSvcActivator(baseDir: String, mandatoryProperties: Option[String] = None): BundleActivator =
    new DominoActivator {

      mandatoryProperties.foreach(s => System.setProperty("blended.updater.profile.properties.keys", s))

      whenBundleActive {
        val ctCtxt = new MockContainerContext(baseDir)
        // This needs to be a fixed uuid as some tests might be for restarts and require the same id
        new ContainerIdentifierServiceImpl(ctCtxt) {
          override lazy val uuid: String = "simple"
        }.providesService[ContainerIdentifierService]
      }
    }

  def withSimpleBlendedContainer[T](
    baseDir: String, mandatoryProperties: List[String] = List.empty
  )(f: BlendedPojoRegistry => T) = {

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
