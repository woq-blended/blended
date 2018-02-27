package blended.security.ssl.internal

import java.security.KeyStore
import java.util.GregorianCalendar
import java.util.Calendar
import java.util.Date
import java.util.TimerTask

import javax.net.ssl.SSLContext

import blended.domino.TypesafeConfigWatching
import blended.security.ssl.{ CertificateProvider, CommonNameProvider, SelfSignedCertificateProvider, SelfSignedConfig }
import blended.security.ssl.X509CertificateInfo
import domino.DominoActivator

import scala.util.{ Failure, Success }
import scala.util.Try
import blended.util.config.Implicits._
import akka.actor.Scheduler
import java.util.Timer
import com.typesafe.config.Config
import domino.capsule.Capsule
import org.osgi.framework.ServiceRegistration
import domino.capsule.CapsuleScope
import blended.mgmt.base.FrameworkService

class CertificateActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    whenTypesafeConfigAvailable { (cfg, idSvc) =>

      // Should we provide a common name provider?
      val commonName = cfg.getString("commonName")
      val logicalNames = cfg.getStringListOption("logicalHostnames").getOrElse(List.empty)
      val cnProvider = new DefaultCommonNameProvider(commonName, logicalNames)

      cnProvider.providesService[CommonNameProvider](Map("type" -> "default"))

      // Sould we provide a CertifacteProvider with a selftsigned certificate?
      cfg.getConfigOption("selfsigned") match {
        case Some(selfCfg) =>
          val selfSignedProvider = new SelfSignedCertificateProvider(SelfSignedConfig.fromConfig(cnProvider, selfCfg))
          selfSignedProvider.providesService[CertificateProvider](Map(
            "provider" -> "default"
          ))
        case None =>
          log.warn("No config entry 'selfsigned' found. Skipping provision of SelfSignedCertificatProvider")
      }

      val certProviderName = cfg.getString("provider", "default")

      log.debug(s"About to watch for CertificateProvider with property provider=${certProviderName}")

      whenAdvancedServicePresent[CertificateProvider](s"(provider=$certProviderName)") { certificateProvider =>
        log.debug(s"Detected CertificateProvider [${certificateProvider}] with property provider=${certProviderName}. Starting to check and get certificate")

        val ctrlCfg = CertControllerConfig.fromConfig(cfg, new PasswordHasher(idSvc.uuid))
        val certCtrl = new CertificateController(ctrlCfg, certificateProvider)

        certCtrl.checkCertificate() match {
          case Failure(e) => log.error(s"Could not obtain Server certificate for container : ${e.getMessage()}")

          case Success(ServerKeyStore(ks, serverCert)) =>
            log.info("Successfully obtained server certificate (and updated KeyStore) for SSLContexts")

            def registerSslContextProvider(ks: KeyStore): CapsuleScope = executeWithinNewCapsuleScope {
              log.debug("Registering SslContextProvider type=client and type=server")
              val sslCtxtProvider = new SslContextProvider(ks, ctrlCfg.keyPass)
              // TODO: what should we do with this side-effect, if we unregister the context provider?
              // FIXME: should this side-effect be configurable?
              SSLContext.setDefault(sslCtxtProvider.serverContext)
              val serverReg = sslCtxtProvider.clientContext.providesService[SSLContext](Map("type" -> "client"))
              val clientReg = sslCtxtProvider.serverContext.providesService[SSLContext](Map("type" -> "server"))

              onStop {
                log.debug("Unregistering SslContextProvider type=client and type=server")
                Try { serverReg.unregister() }
                Try { clientReg.unregister() }
              }
            }

            // Registration of the SslContextProvider, which ne need to stop and re-set from within the refresh-timer
            var regScope = registerSslContextProvider(ks)

            cfg.getConfigOption("refresher") match {
              case None => log.debug("No configuration for automatic certificate refresh found")

              case Some(c) =>
                // Read refresher config
                RefresherConfig.fromConfig(c, ctrlCfg.minValidDays) match {

                  case Failure(e) =>
                    log.error(e)("Could not read refresher config. Skipping automatic certificate refresh")

                  case Success(refresherConfig) =>
                    // we have a refresher config

                    val timerName = "refresh-certificate"
                    val timer = new Timer(timerName, true)

                    /**
                     * Schedule the next refresh attempt for the given certificate.
                     * If the Domino capsule stops, this timer will also be cancelled.
                     */
                    def scheduleRefresh(certCtrl: CertificateController, certInfo: X509CertificateInfo, refresherConfig: RefresherConfig): Unit = {
                      val nextScheduleTime = nextRefreshScheduleTime(certInfo.notAfter, refresherConfig)
                      val task = new RefreshTask(certCtrl, certInfo, refresherConfig)
                      log.debug(s"Scheduling new timer task with timer [${timerName}] to start at ${nextScheduleTime}")
                      timer.schedule(task, nextScheduleTime)
                      onStop {
                        log.debug(s"Cancelling timer [${timerName}]")
                        timer.cancel()
                      }
                    }

                    /**
                     * This task tries to refresh a certificate.
                     * If positive, depending on config, the new certificate is re-published as SslContextProvider or the whole OSGi container will be restarted.
                     */
                    class RefreshTask(certCtrl: CertificateController, certInfo: X509CertificateInfo, refresherConfig: RefresherConfig) extends TimerTask {
                      override def run(): Unit = {
                        log.debug(s"About to start refresh timer task. Trying to update cert [${certInfo}]")
                        // request new cert
                        certCtrl.checkCertificate() match {
                          case Failure(e) =>
                            log.debug(e)("Automatic certifcate refresh failed. Countinuing with old SslContextProvider")
                            scheduleRefresh(certCtrl, certInfo, refresherConfig)

                          case Success(ServerKeyStore(newKs, newServerCert)) =>
                            val newInfo = X509CertificateInfo(newServerCert.chain.head)
                            if (certInfo == newInfo) {
                              // no cert update
                              log.debug("Automatic certificate refresh could not obtain an updated certificate. Continuing with old SslContextProviver")
                              scheduleRefresh(certCtrl, certInfo, refresherConfig)
                            } else {
                              // cert update
                              log.info(s"Automatic certificate refresh returned a new certificate [${newInfo}]")
                              refresherConfig.onRefreshAction match {
                                case RefresherConfig.Refresh =>

                                  log.info("About to remove old SslContextProvider from registry")
                                  regScope.stop()

                                  log.info("Registering new SslContextProvider for new KeyStore")
                                  regScope = registerSslContextProvider(newKs)
                                  scheduleRefresh(certCtrl, newInfo, refresherConfig)

                                case RefresherConfig.Restart =>
                                  withService[FrameworkService, Unit] {
                                    case Some(frameworkService) =>
                                      // we want to restart the container, so no de- and re-registration of the ssl context
                                      log.warn("Requesting framework restart")
                                      frameworkService.restartContainer("The certificate required for the SSL context was refreshed. An restart is required to cleanly use the new certificate.", true)

                                    case None =>
                                      log.error("Could not aquire a FrameworkService to restart the OSGi container. Skipping certificate refresh.")
                                      scheduleRefresh(certCtrl, certInfo, refresherConfig)
                                  }
                              }
                            }
                        }
                      }
                    }

                    // initial start the timer
                    scheduleRefresh(certCtrl, X509CertificateInfo(serverCert.chain.head), refresherConfig)

                }
            }

        }
      }
    }
  }

  def nextRefreshScheduleTime(validEnd: Date, refresherConfig: RefresherConfig, now: Option[Date] = None): Date = {
    // calc next schedule date
    val cal = new GregorianCalendar()
    cal.clear()
    cal.setTime(validEnd)
    cal.add(Calendar.DAY_OF_MONTH, -1 * refresherConfig.minValidDays)

    val curNow = now.map(_.getTime()).getOrElse(System.currentTimeMillis())

    // ensure the threshold is not in the past
    val threshold = math.max(cal.getTimeInMillis(), curNow)
    cal.setTimeInMillis(threshold)

    // apply configured check time
    cal.set(Calendar.HOUR_OF_DAY, refresherConfig.hourOfDay)
    cal.set(Calendar.MINUTE, refresherConfig.minuteOfDay)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    if (cal.getTimeInMillis() < threshold) {
      cal.add(Calendar.DAY_OF_MONTH, 1)
    }

    cal.getTime()
  }
}
