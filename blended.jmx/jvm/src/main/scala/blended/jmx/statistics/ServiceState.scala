package blended.jmx.statistics

sealed trait ServiceState
object ServiceState {
  final case object Started extends ServiceState
  final case object Completed extends ServiceState
  final case object Failed extends ServiceState
}
