package blended.jmx.statistics

case class Accumulator(
  count: Long = 0,
  totalMsec: Long = 0,
  minMsec: Long = Long.MaxValue,
  maxMsec: Long = Long.MinValue
) {
  def record(msec: Long): Accumulator = {
    copy(
      count = count + 1,
      totalMsec = totalMsec + msec,
      minMsec = Math.min(minMsec, msec),
      maxMsec = Math.max(maxMsec, msec)
    )
  }

  val avg: Double = if (count == 0) {
    0d
  } else {
    totalMsec.toDouble / count
  }
}
