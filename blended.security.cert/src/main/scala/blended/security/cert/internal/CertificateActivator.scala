package blended.security.cert.internal

import javax.net.ssl.SSLContext

import blended.domino.TypesafeConfigWatching
import blended.security.cert.CertificateProvider
import domino.DominoActivator

import scala.util.{Failure, Success}

class CertificateActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    whenTypesafeConfigAvailable{ (cfg, idSvc) =>

      val selfCfg = cfg.getConfig("selfsigned")

      val selfSignedProvider = new SelfSignedCertificateProvider(SelfSignedConfig.fromConfig(selfCfg))

      selfSignedProvider.providesService[CertificateProvider](Map(
        "provider" -> "default"
      ))

      val certProviderName = if (cfg.hasPath("provider")) Some(cfg.getString("provider")) else "default"

      whenAdvancedServicePresent[CertificateProvider](s"(provider=$certProviderName)"){ p =>
        val ctrlCfg = CertControllerConfig.fromConfig(cfg, new PasswordHasher(idSvc.uuid))
        val certCtrl = new CertificateController(ctrlCfg, p)

        certCtrl.checkCertificate() match {
          case Success(ks) =>
            log.info("Successfully obtained server certificate for SSLContexts.")
            val sslCtxtProvider = new SslContextProvider(ks, ctrlCfg.keyPass)

            SSLContext.setDefault(sslCtxtProvider.serverContext)

            sslCtxtProvider.clientContext.providesService[SSLContext](Map("type" -> "client"))
            sslCtxtProvider.serverContext.providesService[SSLContext](Map("type" -> "server"))
          case Failure(e) =>
            log.error(s"Could not obtain Server certificate for container : ${e.getMessage()}")
        }
      }
    }
  }
}
