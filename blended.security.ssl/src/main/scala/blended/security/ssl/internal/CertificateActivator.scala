package blended.security.ssl.internal

import blended.container.context.ContainerIdentifierService
import blended.domino.TypesafeConfigWatching
import blended.security.ssl.{CertificateProvider, SelfSignedCertificateProvider, SelfSignedConfig}
import blended.util.config.Implicits._
import com.typesafe.config.Config
import domino.DominoActivator

class CertificateActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = org.log4s.getLogger

  private[this] def setupSelfSignedProvider(cfg: Config, idSvc: ContainerIdentifierService) : Unit = {
    // Sould we provide a CertifacteProvider with a self-signed certificate?
    cfg.getConfigOption("selfsigned") match {
      case Some(selfCfg) =>
        val selfSignedProvider = new SelfSignedCertificateProvider(SelfSignedConfig.fromConfig(selfCfg, idSvc))
        selfSignedProvider.providesService[CertificateProvider](Map(
          "provider" -> "default"
        ))
      case None =>
        log.warn("No config entry 'selfsigned' found. Skipping provision of SelfSignedCertificateProvider")
    }
  }

  private[this] def setupCertificateManager(cfg: Config, idSvc: ContainerIdentifierService) : Unit = {

    val mgrConfig = CertificateManagerConfig.fromConfig(cfg, new PasswordHasher(idSvc.uuid))
    val mgr = new CertificateManager(bundleContext, capsuleContext, mgrConfig, Map.empty)

    addCapsule(mgr)
  }

  whenBundleActive {
    whenTypesafeConfigAvailable { (cfg, idSvc) =>
      setupSelfSignedProvider(cfg, idSvc)
      setupCertificateManager(cfg, idSvc)
    }
  }

}
