package blended.security.ssl.internal

import blended.container.context.api.ContainerIdentifierService
import blended.domino.TypesafeConfigWatching
import blended.security.ssl.{CertificateManager, CertificateProvider, SelfSignedCertificateProvider, SelfSignedConfig}
import blended.util.config.Implicits._
import blended.util.logging.Logger
import com.typesafe.config.Config
import domino.DominoActivator
import javax.management.{MBeanServer, ObjectName}
import javax.net.ssl.SSLContext

class CertificateActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = Logger[CertificateActivator]

  private[this] def setupSelfSignedProvider(cfg: Config, idSvc: ContainerIdentifierService) : Unit = {
    // Should we provide a CertifacteProvider with a self-signed certificate?
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

  private[this] def setupCertificateManager(
    mgrConfig: CertificateManagerConfig
  ) : Unit = {

    def waitForProvider(providerNames: List[String], provider: Map[String, CertificateProvider]) : Unit = {
      providerNames match {
        case Nil =>
          val mgr = new CertificateManagerImpl(bundleContext, capsuleContext, mgrConfig, provider)
          mgr.providesService[CertificateManager]
          addCapsule(mgr)
        case head :: tail =>
          log.info(s"Waiting for certificate provider [$head]")
          whenAdvancedServicePresent[CertificateProvider](s"(provider=$head)") { p =>
            log.info(s"Certificate provider [$head] available.")
            waitForProvider(tail, provider + (head ->  p))
          }
      }
    }

    val distinctProviderNames : List[String] = mgrConfig.certConfigs.map(_.provider).distinct
    waitForProvider(distinctProviderNames, Map.empty)
  }

  whenBundleActive {

    whenTypesafeConfigAvailable { (cfg, idSvc) =>

      val mgrConfig = CertificateManagerConfig.fromConfig(cfg, new PasswordHasher(idSvc.uuid), idSvc)

      setupSelfSignedProvider(cfg, idSvc)
      setupCertificateManager(mgrConfig)

      whenAdvancedServicePresent[SSLContext]("(type=server)") { ctxt =>
        val info = new SslContextInfo(ctxt, mgrConfig.validCypherSuites)
        info.providesService[blended.security.ssl.SslContextInfo]

        whenServicePresent[MBeanServer] { server =>
          val objName: ObjectName = new ObjectName("blended:type=SslContext,name=server")
          server.registerMBean(info, objName)

          onStop {
            server.unregisterMBean(objName)
          }
        }
      }
    }
  }
}
