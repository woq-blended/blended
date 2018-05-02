package blended.akka.http.proxy.internal

import com.typesafe.config.Config
import blended.util.config.Implicits._
import scala.util.Try

case class ProxyConfig(paths: Seq[ProxyTarget])

object ProxyConfig {
  def parse(cfg: Config): Try[ProxyConfig] = Try {
    ProxyConfig(
      cfg.getConfigMap("paths", Map()).toList.map {
        case (k, v) =>
          ProxyTarget(
            path = k,
            uri = v.getString("uri"),
            timeout = v.getInt("timeout", 10)
          )
      }
    )
  }

}

case class ProxyTarget(
  path: String,
  uri: String,
  timeout: Int
) {

  def isHttps: Boolean = uri.substring(0, 5).equalsIgnoreCase("https")

  override def toString(): String = getClass().getSimpleName() +
    "(path=" + path +
    ",uri=" + uri +
    ",timeout=" + timeout +
    ")"
}


