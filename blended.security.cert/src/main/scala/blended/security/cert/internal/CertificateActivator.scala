package blended.security.cert.internal

import blended.domino.TypesafeConfigWatching
import blended.security.cert.{CertificateProvider, SelfSignedConfig}
import domino.DominoActivator

class CertificateActivator extends DominoActivator with TypesafeConfigWatching {

  whenBundleActive {
    whenTypesafeConfigAvailable{ (cfg, _) =>

      new SelfSignedCertificateProvider(SelfSignedConfig.fromConfig(cfg))
        .providesService[CertificateProvider](Map(
        "type" -> "server",
        "provider" -> "default"
      ))
    }
  }
}
