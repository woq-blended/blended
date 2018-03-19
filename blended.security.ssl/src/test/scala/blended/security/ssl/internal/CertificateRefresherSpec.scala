package blended.security.ssl.internal

import org.scalatest.FreeSpec

import blended.testsupport.pojosr.PojoSrTestHelper
import java.util.GregorianCalendar
import java.util.Date
import java.util.concurrent.TimeUnit

class CertificateRefresherSpec extends FreeSpec with PojoSrTestHelper {

  //  val dateBase = new Date(100, 0, 1).getTime()
  def day(n: Long = 1): Long = TimeUnit.DAYS.toMillis(n)

  implicit class RichDate(date: Date) {
    def <(other: Date): Boolean = date.before(other)
    def <=(other: Date): Boolean = !date.after(other)
    def >(other: Date): Boolean = date.after(other)
    def >=(other: Date): Boolean = !date.before(other)
  }

  "Automatic certificate refresher" - {
    "Calculation of the best next schedule time for an refresh attempt" - {

      val refresherConfig = RefresherConfig(
        minValidDays = 10,
        hourOfDay = 1,
        minuteOfDay = 30,
        onRefreshAction = RefresherConfig.Refresh)

      "cert end + threshold is in future" in {
        val now = new Date(100, 5, 1)
        val validEnd = new Date(100, 5, 20)
        val date = CertificateRefresher.nextRefreshScheduleTime(validEnd, refresherConfig, Some(now))
        assert(date === new Date(100, 5, 10, 1, 30))
      }

      "cert end is in future, cert end + threshold not" - {
        "schedule should be on same day" in {
          val now = new Date(100, 5, 1)
          val validEnd = new Date(100, 5, 5)
          val date = CertificateRefresher.nextRefreshScheduleTime(validEnd, refresherConfig, Some(now))
          assert(date === new Date(100, 5, 1, 1, 30))
        }

        "schedule should be on next day" in {
          val now = new Date(100, 5, 1, 2, 0)
          val validEnd = new Date(100, 5, 5)
          val date = CertificateRefresher.nextRefreshScheduleTime(validEnd, refresherConfig, Some(now))
          assert(date === new Date(100, 5, 2, 1, 30))
        }
      }

      "cert end is in not in the future" - {
        "schedule should be on same day" in {
          val now = new Date(100, 5, 1)
          val validEnd = new Date(100, 4, 20)
          val date = CertificateRefresher.nextRefreshScheduleTime(validEnd, refresherConfig, Some(now))
          assert(date === new Date(100, 5, 1, 1, 30))
        }
        "schedule should be on next day" in {
          val now = new Date(100, 5, 1, 2, 0)
          val validEnd = new Date(100, 4, 20)
          val date = CertificateRefresher.nextRefreshScheduleTime(validEnd, refresherConfig, Some(now))
          assert(date === new Date(100, 5, 2, 1, 30))
        }
      }
    }
  }

}