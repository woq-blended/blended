package blended.security.scep.internal

import blended.domino.TypesafeConfigWatching
import blended.security.ssl.{CertificateProvider, CommonNameProvider}
import blended.util.config.Implicits._
import domino.DominoActivator

class ScepActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    whenTypesafeConfigAvailable{ (cfg, _) =>

      val scepUrl = cfg.getStringOption("scepUrl")

      scepUrl.foreach { url =>

        val cnProviderName = cfg.getString("cnProvider", "default")
        val profile = cfg.getStringOption("scepProfile")
        val keyLength = cfg.getInt("keyLength", 2048)
        val csrSignAlgorithm = cfg.getString("csrSignAlgorithm", "SHA1withRSA")
        val scepChallenge = cfg.getString("scepChallenge")
        log.debug(s"Waiting for CommonNameProvider of type [$cnProviderName]")

        whenAdvancedServicePresent[CommonNameProvider](s"(type=$cnProviderName)") { cnProvider =>
          val cfg = ScepConfig(
            url = url,
            cnProvider = cnProvider,
            profile = profile,
            keyLength = keyLength,
            csrSignAlgorithm = csrSignAlgorithm,
            scepChallenge = scepChallenge
          )
          new ScepCertificateProvider(cfg).providesService[CertificateProvider](Map("provider" -> "scep"))
        }
      }
    }
  }
}
