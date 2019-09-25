package blended.jmx.statistics

case class StatisticData(
  name: String,
  id: String,
  state: ServiceState,
  timeStamp: Long = System.currentTimeMillis()
)
