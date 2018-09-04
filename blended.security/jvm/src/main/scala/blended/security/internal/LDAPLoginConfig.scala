package blended.security.internal

import com.typesafe.config.Config
import blended.util.config.Implicits._

object LDAPLoginConfig {

  def fromConfig(cfg: Config) : LDAPLoginConfig = LDAPLoginConfig(
    url = cfg.getString("url"),
    systemUser = cfg.getStringOption("systemUser"),
    systemPassword = cfg.getStringOption("systemPassword"),
    userBase = cfg.getString("userBase"),
    userSearch = cfg.getString("userSearch"),
    groupBase = cfg.getString("groupBase"),
    groupSearch = cfg.getString("groupSearch"),
    groupAttribute = cfg.getString("groupAttribute"),
    expandSearch = cfg.getStringOption("expandSearch")
  )
}

case class LDAPLoginConfig (
  url : String,
  systemUser : Option[String],
  systemPassword: Option[String],
  userBase : String,
  userSearch : String,
  groupBase : String,
  groupSearch : String,
  groupAttribute : String,
  expandSearch: Option[String]
)

