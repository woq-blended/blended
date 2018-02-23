package blended.security.ssl.internal

import scala.util.Try

import com.typesafe.config.Config

import blended.util.config.Implicits._

case class RefresherConfig(
  minValidDays: Int,
  hourOfDay: Int,
  minuteOfDay: Int,
  onRefreshAction: RefresherConfig.Action)

object RefresherConfig {

  sealed trait Action
  object Action {
    def fromString(action: String): Try[Action] = Try {
      action match {
        case "refresh" => Refresh
        case "restart" => Restart
        case _ => sys.error("Unsupported action name: " + action)
      }
    }
  }
  case object Refresh extends Action
  case object Restart extends Action

  def fromConfig(config: Config, defaultMinValidDays: Int): Try[RefresherConfig] = Try {
    RefresherConfig(
      minValidDays = config.getInt("minValidDays", defaultMinValidDays),
      hourOfDay = config.getInt("hour", 0),
      minuteOfDay = config.getInt("minute", 0),
      onRefreshAction = RefresherConfig.Action.fromString(config.getString("onRefreshAction", "refresh")).get)
  }
}