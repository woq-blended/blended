package blended.security.internal

import blended.container.context.api.ContainerIdentifierService
import com.typesafe.config.Config
import blended.util.config.Implicits._

object LDAPLoginConfig {

  // TODO: Review passing of ContainerIdentifierService
  def fromConfig(cfg: Config, idSvc: ContainerIdentifierService) : LDAPLoginConfig = {

    val resolve : String => String = s =>
      idSvc.resolvePropertyString(s).map(_.toString).get

    LDAPLoginConfig(
      url = resolve(cfg.getString("url")),
      systemUser = cfg.getStringOption("systemUser").map(resolve),
      systemPassword = cfg.getStringOption("systemPassword").map(resolve),
      userBase = resolve(cfg.getString("userBase")),
      userSearch = resolve(cfg.getString("userSearch")),
      groupBase = resolve(cfg.getString("groupBase")),
      groupSearch = resolve(cfg.getString("groupSearch")),
      groupAttribute = resolve(cfg.getString("groupAttribute")),
      expandSearch = cfg.getStringOption("expandSearch").map(resolve)
    )
  }
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

