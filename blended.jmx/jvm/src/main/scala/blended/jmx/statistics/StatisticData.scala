package blended.jmx.statistics

sealed trait ServiceState

object ServiceState {
  final case object Started extends ServiceState {
    override def toString: String = "Started"
  }

  final case object Completed extends ServiceState {
    override def toString: String = "Completed"
  }

  final case object Failed extends ServiceState {
    override def toString: String = "Failed"
  }
}

case class StatisticData(
  component : String,
  subComponent : Option[String] = None,
  id: String,
  state: ServiceState,
  timeStamp: Long = System.currentTimeMillis()
) {
  override def toString: String = s"${getClass().getSimpleName()}(component=$component, subComponent=$subComponent, id=$id, state=$state, timestamp=$timeStamp)"
}
