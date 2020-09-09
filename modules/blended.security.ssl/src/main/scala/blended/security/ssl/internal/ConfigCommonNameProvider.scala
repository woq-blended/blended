package blended.security.ssl.internal

import blended.container.context.api.ContainerContext
import blended.security.ssl.CommonNameProvider
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.util.Try

class ConfigCommonNameProvider(cfg : Config, ctCtxt : ContainerContext) extends CommonNameProvider {

  override def commonName() : Try[String] = ctCtxt.resolveString(cfg.getString("commonName")).map(_.toString())

  override def alternativeNames() : Try[List[String]] = Try {
    cfg.getStringListOption("logicalHostnames").getOrElse(List.empty).map { s =>
      ctCtxt.resolveString(s).map(_.toString()).get
    }
  }
}
