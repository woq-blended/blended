package blended.security.ssl.internal

import blended.container.context.api.ContainerIdentifierService
import blended.security.ssl.CommonNameProvider
import com.typesafe.config.Config
import blended.util.config.Implicits._

import scala.util.Try

class ConfigCommonNameProvider (cfg : Config, idSvc: ContainerIdentifierService) extends CommonNameProvider {

  override def commonName(): Try[String] = idSvc.resolvePropertyString(cfg.getString("commonName")).map(_.toString())

  override def alternativeNames(): Try[List[String]] = Try {
    cfg.getStringListOption("logicalHostnames").getOrElse(List.empty).map{ s =>
      idSvc.resolvePropertyString(s).map(_.toString()).get
    }
  }
}
