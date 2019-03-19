package blended.security.scep.internal

import blended.domino.TypesafeConfigWatching
import blended.security.ssl.CertificateProvider
import blended.util.config.Implicits._
import blended.util.logging.Logger
import domino.DominoActivator

class ScepActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = Logger[ScepActivator]

  whenBundleActive {
    whenTypesafeConfigAvailable{ (cfg, _) =>

      val scepUrl = cfg.getStringOption("scepUrl")

      scepUrl.foreach { url =>

        val profile = cfg.getStringOption("scepProfile")
        val keyLength = cfg.getInt("keyLength", 2048)
        val csrSignAlgorithm = cfg.getString("csrSignAlgorithm", "SHA1withRSA")
        val scepChallenge = cfg.getString("scepChallenge")

        val scepCfg = ScepConfig(
          url = url,
          profile = profile,
          keyLength = keyLength,
          csrSignAlgorithm = csrSignAlgorithm,
          scepChallenge = scepChallenge
        )
        new ScepCertificateProvider(scepCfg).providesService[CertificateProvider]("provider" -> "scep")
      }
    }
  }
}
