package blended.security.scep.internal

import blended.domino.TypesafeConfigWatching
import blended.security.ssl.CertificateProvider
import blended.util.config.Implicits._
import domino.DominoActivator

class ScepActivator extends DominoActivator with TypesafeConfigWatching {

  whenBundleActive {
    whenTypesafeConfigAvailable { (cfg, ctCtxt) =>

      val scepUrl = cfg.getStringOption("scepUrl").map(ctCtxt.resolveString(_).get.asInstanceOf[String])

      scepUrl.foreach { url =>

        val profile : Option[String] = cfg.getStringOption("scepProfile")
        val keyLength : Int = cfg.getInt("keyLength", 2048)
        val csrSignAlgorithm : String = cfg.getString("csrSignAlgorithm", "SHA1withRSA")
        val scepChallenge : String = ctCtxt.resolveString(cfg.getString("scepChallenge")).map(_.toString()).get

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
