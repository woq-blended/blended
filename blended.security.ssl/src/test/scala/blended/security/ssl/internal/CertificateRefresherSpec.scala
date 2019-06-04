package blended.security.ssl.internal

import java.io.File
import java.util.{Calendar, Date, GregorianCalendar}

import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.PojoSrTestHelper
import org.scalatest.FreeSpec

class CertificateRefresherSpec extends FreeSpec with PojoSrTestHelper {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput).getAbsolutePath()

  def newDate(year : Int, month : Int, day : Int, hour : Int = 0, minute : Int = 0) : Date = {
    val cal : GregorianCalendar = new GregorianCalendar(year, month, day, hour, minute, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.getTime()
  }

  implicit class RichDate(date : Date) {
    def <(other : Date) : Boolean = date.before(other)
    def <=(other : Date) : Boolean = !date.after(other)
    def >(other : Date) : Boolean = date.after(other)
    def >=(other : Date) : Boolean = !date.before(other)
  }

  "Automatic certificate refresher" - {
    "Calculation of the best next schedule time for an refresh attempt" - {

      val refresherConfig = RefresherConfig(
        minValidDays = 10,
        hourOfDay = 1,
        minuteOfDay = 30,
        onRefreshAction = RefresherConfig.Refresh
      )

      "cert end + threshold is in future" in {
        val now = newDate(100, 5, 1)
        val validEnd = newDate(100, 5, 20)
        val date = CertificateRefresher.nextRefreshScheduleTime(validEnd, refresherConfig, Some(now))
        assert(date === newDate(100, 5, 10, 1, 30))
      }

      "cert end is in future, cert end + threshold not" - {
        "schedule should be on same day" in {
          val now = newDate(100, 5, 1)
          val validEnd = newDate(100, 5, 5)
          val date = CertificateRefresher.nextRefreshScheduleTime(validEnd, refresherConfig, Some(now))
          assert(date === newDate(100, 5, 1, 1, 30))
        }

        "schedule should be on next day" in {
          val now = newDate(100, 5, 1, 2, 0)
          val validEnd = newDate(100, 5, 5)
          val date = CertificateRefresher.nextRefreshScheduleTime(validEnd, refresherConfig, Some(now))
          assert(date === newDate(100, 5, 2, 1, 30))
        }
      }

      "cert end is in not in the future" - {
        "schedule should be on same day" in {
          val now = newDate(100, 5, 1)
          val validEnd = newDate(100, 4, 20)
          val date = CertificateRefresher.nextRefreshScheduleTime(validEnd, refresherConfig, Some(now))
          assert(date === newDate(100, 5, 1, 1, 30))
        }
        "schedule should be on next day" in {
          val now = newDate(100, 5, 1, 2, 0)
          val validEnd = newDate(100, 4, 20)
          val date = CertificateRefresher.nextRefreshScheduleTime(validEnd, refresherConfig, Some(now))
          assert(date === newDate(100, 5, 2, 1, 30))
        }
      }
    }
  }
}
