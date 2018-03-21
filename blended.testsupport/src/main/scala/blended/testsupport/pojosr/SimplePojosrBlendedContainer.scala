package blended.testsupport.pojosr

import blended.container.context.ContainerIdentifierService
import blended.container.context.impl.internal.IdServiceFactory
import blended.updater.config.RuntimeConfig
import org.apache.felix.connect.launch.PojoServiceRegistry
import org.osgi.framework.BundleActivator

trait SimplePojosrContainer { this : PojoSrTestHelper =>

  private[this] def idSvcActivator(baseDir : String, mandatoryProperties : Option[String] = None) : BundleActivator = new DominoActivator {

    mandatoryProperties.foreach(s => System.setProperty(RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS, s))

    whenBundleActive {
      val ctCtxt = new MockContainerContext(baseDir)
      IdServiceFactory.idSvc(ctCtxt).providesService[ContainerIdentifierService]
    }
  }

  def withSimpleBlendedContainer[T](baseDir: String)(f: PojoServiceRegistry => T) = withPojoServiceRegistry[T] { sr =>
    withStartedBundle[T](idSvcActivator(baseDir)){ sr =>
      f(sr)
    }
  }

}
