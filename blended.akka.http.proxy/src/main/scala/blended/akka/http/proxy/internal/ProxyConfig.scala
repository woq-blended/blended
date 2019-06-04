package blended.akka.http.proxy.internal

import blended.akka.http.proxy.internal.RedirectHeaderPolicy.RedirectHeaderPolicy
import com.typesafe.config.Config
import blended.util.config.Implicits._

import scala.util.Try

object RedirectHeaderPolicy extends Enumeration {

  type RedirectHeaderPolicy = Value

  val Client_Only = Value("Client_Only")
  val Redirect_Replace = Value("Redirect_Replace")
  val Redirect_Merge = Value("Redirect_Merge")
}

case class ProxyConfig(
  context : String,
  paths : Seq[ProxyTarget]
)

object ProxyConfig {
  def parse(cfg : Config) : Try[ProxyConfig] = Try {
    ProxyConfig(
      cfg.getString("context"),
      cfg.getConfigMap("paths", Map()).toList.map {
        case (k, v) =>
          ProxyTarget(
            path = k,
            uri = v.getString("uri"),
            timeout = v.getInt("timeout", 10),
            redirectCount = v.getInt("redirectCount", 0),
            redirectHeaderPolicy = cfg.getStringOption("headerPolicy") match {
              case Some(s) => try {
                RedirectHeaderPolicy.withName(s)
              } catch {
                case _ : Throwable => RedirectHeaderPolicy.Client_Only
              }
              case None => RedirectHeaderPolicy.Client_Only
            }
          )
      }
    )
  }
}

case class ProxyTarget(
  path : String,
  uri : String,
  timeout : Int,
  redirectCount : Int = 0,
  redirectHeaderPolicy : RedirectHeaderPolicy = RedirectHeaderPolicy.Client_Only
) {

  def isHttps : Boolean = uri.substring(0, 5).equalsIgnoreCase("https")

  override def toString() : String = getClass().getSimpleName() +
    "(path=" + path +
    ",uri=" + uri +
    ",timeout=" + timeout +
    ",redirectCount=" + redirectCount +
    ",redirectHeaderPolicy=" + redirectHeaderPolicy.toString() +
    ")"
}

