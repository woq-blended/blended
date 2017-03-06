package blended.util

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._

package protocol {

  case class  IncrementCounter(val count : Int = 1)
  case object QueryCounter
  case class  CounterInfo(
    count : Int,
    firstCount: Option[Long],
    lastCount: Option[Long]
  ) {

    def interval : Duration =
      if (firstCount.isDefined && lastCount.isDefined)
        (lastCount.get - firstCount.get).millis
      else
        0.millis

    def speed(unit: TimeUnit = TimeUnit.MILLISECONDS) = {
      interval.length match {
        case 0 => if (count == 0) 0.0 else Double.MaxValue
        case _ => count.asInstanceOf[Double] / (interval.length) * unit.toMillis(1)
      }
    }
  }

  case object StopCounter

}
