package blended.testsupport.pojosr

import java.util.UUID

import blended.container.context.api.ContainerIdentifierService
import blended.container.context.impl.internal.ContainerIdentifierServiceImpl
import domino.DominoActivator
import org.apache.felix.connect.launch.PojoServiceRegistry
import org.osgi.framework.BundleActivator

trait SimplePojosrBlendedContainer { this : PojoSrTestHelper =>

  private[this] def idSvcActivator(baseDir : String, mandatoryProperties : Option[String] = None) : BundleActivator = new DominoActivator {

    mandatoryProperties.foreach(s => System.setProperty("blended.updater.profile.properties.keys", s))

    whenBundleActive {
      val ctCtxt = new MockContainerContext(baseDir)
      new ContainerIdentifierServiceImpl(ctCtxt) {
        override lazy val uuid: String = UUID.randomUUID().toString()
      }.providesService[ContainerIdentifierService]
    }
  }

  def withSimpleBlendedContainer[T](
    baseDir: String, mandatoryProperties : List[String] = List.empty
  )(f: BlendedPojoRegistry => T) = {

    System.setProperty("blended.home", baseDir)

    withPojoServiceRegistry[T] { sr =>
      withStartedBundle[T](sr)(
        classOf[ContainerIdentifierServiceImpl].getPackage().getName(),
        Some(() => idSvcActivator(baseDir, Some(mandatoryProperties.mkString(","))))
      )(f)
    }
  }
}
