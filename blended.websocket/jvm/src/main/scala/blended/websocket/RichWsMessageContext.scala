package blended.websocket

import scala.language.implicitConversions
import akka.http.scaladsl.model.StatusCode

object RichWsMessageContext {
  implicit def toRichContext(c : WsMessageContext) : RichWsMessageContext =
    new RichWsMessageContext(c)
}

class RichWsMessageContext(env : WsMessageContext) {

  def withStatus(s : StatusCode) : WsMessageContext = env.withStatus(s.intValue())
  def withStatus(s : StatusCode, m : String) : WsMessageContext = env.withStatus(s.intValue(), m)

}
