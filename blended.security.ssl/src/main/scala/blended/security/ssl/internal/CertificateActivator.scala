package blended.security.ssl.internal

import javax.net.ssl.SSLContext
import blended.domino.TypesafeConfigWatching
import blended.security.ssl.{ CertificateProvider, CommonNameProvider, SelfSignedCertificateProvider, SelfSignedConfig }
import blended.security.ssl.X509CertificateInfo
import domino.DominoActivator

import scala.util.{ Failure, Success }
import blended.util.config.Implicits._

class CertificateActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    whenTypesafeConfigAvailable { (cfg, idSvc) =>

      val commonNameConfigPath = "commonName"
      val selfsignedConfigPath = "selfsigned"

      if (cfg.hasPath(commonNameConfigPath)) {
        val commonName = cfg.getString(commonNameConfigPath)
        new DefaultCommonNameProvider(commonName).providesService[CommonNameProvider](Map("type" -> "default"))
      } else {
        log.warn("No config entry 'commonName' found. Skipping provision of default CommonNameProvider.")
      }

      if (cfg.hasPath(selfsignedConfigPath)) {
        val selfCfg = cfg.getConfig("selfsigned")
        val selfSignedProvider = new SelfSignedCertificateProvider(SelfSignedConfig.fromConfig(selfCfg))

        selfSignedProvider.providesService[CertificateProvider](Map(
          "provider" -> "default"
        ))
      } else {
        log.warn("No config entry 'selfsigned' found. Skipping provision of SelfSignedCertificatProvider")
      }

      val certProviderName = cfg.getString("provider", "default")

      log.debug(s"About to watch for CertificateProvider with property provider=${certProviderName}")
      whenAdvancedServicePresent[CertificateProvider](s"(provider=$certProviderName)") { certificateProvider =>
        log.debug(s"Detected CertificateProvider [${certificateProvider}] with property provider=${certProviderName}. Starting to check and get certificate")

        val ctrlCfg = CertControllerConfig.fromConfig(cfg, new PasswordHasher(idSvc.uuid))
        val certCtrl = new CertificateController(ctrlCfg, certificateProvider)

        certCtrl.checkCertificate() match {
          case Success(ServerKeyStore(ks, serverCert)) =>
            log.info("Successfully obtained server certificate for SSLContexts.")
            val sslCtxtProvider = new SslContextProvider(ks, ctrlCfg.keyPass)

            // TODO: if config says so, recheck the validity of the given certificate
            X509CertificateInfo(serverCert.chain.head)

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
