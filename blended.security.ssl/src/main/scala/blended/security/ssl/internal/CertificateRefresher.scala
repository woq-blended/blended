package blended.security.ssl.internal

import java.util._

import blended.mgmt.base.FrameworkService
import blended.util.logging.Logger
import domino.capsule.{Capsule, CapsuleScope}
import domino.service_consuming.ServiceConsuming
import org.osgi.framework.BundleContext

import scala.util.{Failure, Success}

object CertificateRefresher {

  def nextRefreshScheduleTime(validEnd : Date, refresherConfig : RefresherConfig, now : Option[Date] = None) : Date = {
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

class CertificateRefresher(
  override val bundleContext : BundleContext,
  certMgr : CertificateManagerImpl,
  cfg : RefresherConfig,
  scope : CapsuleScope
) extends Capsule with ServiceConsuming {

  private[this] val log = Logger[CertificateRefresher]

  private[this] val timerName = "refresh-certificate"
  private[this] val timer = new Timer(timerName, true)

  override def start() : Unit = scheduleRefresh(cfg)

  override def stop() : Unit = {
    log.debug(s"Cancelling timer [$timerName]")
    timer.cancel()
  }

  /**
   * Schedule the next refresh attempt for the given certificate.
   * If the Domino capsule stops, this timer will also be cancelled.
   */
  def scheduleRefresh(refresherConfig : RefresherConfig) : Unit = {

    certMgr.nextCertificateTimeout().get.foreach { timeOut =>
      val nextScheduleTime = CertificateRefresher.nextRefreshScheduleTime(timeOut, refresherConfig)
      val task = new RefreshTask(certMgr, refresherConfig)
      log.debug(s"Scheduling new timer task with timer [$timerName] to start at [$nextScheduleTime]")
      timer.schedule(task, nextScheduleTime)
    }
  }

  /**
   * This task tries to refresh a certificate.
   * If positive, depending on config, the new certificate is re-published as SslContextProvider or the whole OSGi container will be restarted.
   */
  class RefreshTask(certMgr : CertificateManagerImpl, refresherConfig : RefresherConfig) extends TimerTask {
    override def run() : Unit = {
      log.debug(s"About to start refresh timer task. Trying to update certificate(s)")
      // request new cert
      certMgr.checkCertificates() match {
        case Failure(e) =>
          log.debug(e)("Automatic certifcate refresh failed. Countinuing with old SslContextProvider")
          scheduleRefresh(refresherConfig)

        case Success(None) =>
        // do nothing

        case Success(Some(newKs)) =>
          if (newKs.changedAliases.isEmpty) {
            // no cert update
            log.debug("Automatic certificate refresh could not obtain an updated certificate. Continuing with old SslContextProviver")
            scheduleRefresh(refresherConfig)
          } else {
            // cert update
            log.info(s"Automatic certificate refresh returned new certificate(s)")
            refresherConfig.onRefreshAction match {
              case RefresherConfig.Refresh =>

                log.info("About to remove old SslContextProvider from registry")
                scope.stop()

                log.info("Registering new SslContextProvider for new KeyStore")
                certMgr.registerSslContextProvider()
                scheduleRefresh(refresherConfig)

              case RefresherConfig.Restart =>
                withService[FrameworkService, Unit] {
                  case Some(frameworkService) =>
                    // we want to restart the container, so no de- and re-registration of the ssl context
                    log.warn("Requesting framework restart")
                    frameworkService.restartContainer("The certificate required for the SSL context was refreshed. An restart is required to cleanly use the new certificate.", true)

                  case None =>
                    log.error("Could not acquire a FrameworkService to restart the OSGi container. Skipping certificate refresh.")
                    scheduleRefresh(refresherConfig)
                }
            }
          }
      }
    }
  }
}
