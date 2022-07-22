package blended.security.internal

import blended.container.context.api.ContainerContext
import blended.util.config.Implicits._
import com.typesafe.config.Config

object LDAPLoginConfig {

  // TODO: Review passing of ContainerIdentifierService
  def fromConfig(cfg : Config, ctCtxt : ContainerContext) : LDAPLoginConfig = {

    val resolve : String => String = s =>
      ctCtxt.resolveString(s).map(_.toString).get

    LDAPLoginConfig(
      url = resolve(cfg.getString("url")),
      systemUser = cfg.getStringOption("systemUser").map(resolve),
      systemPassword = cfg.getStringOption("systemPassword").map(resolve),
      userBase = resolve(cfg.getString("userBase")),
      userAttribute = resolve(cfg.getString("userAttribute")),
      groupBase = resolve(cfg.getString("groupBase")),
      groupSearch = resolve(cfg.getString("groupSearch")),
      groupAttribute = resolve(cfg.getString("groupAttribute")),
      expandSearch = cfg.getStringOption("expandSearch").map(resolve)
    )
  }
}

case class LDAPLoginConfig(
  url : String,
  systemUser : Option[String],
  systemPassword : Option[String],
  userBase : String,
  userAttribute : String,
  groupBase : String,
  groupSearch : String,
  groupAttribute : String,
  expandSearch : Option[String]
)

