package blended.spray

import spray.http.{HttpHeader, HttpHeaders, HttpMessage}

trait HttpHeaderSupport {

  def header(msg: HttpMessage, name: String) : Option[HttpHeader] = msg.headers.filter(_.name == name) match {
    case Nil => None
    case h :: _ => Some(h)
  }

  def isText(msg: HttpMessage) : Boolean = {
    header(msg, HttpHeaders.`Content-Type`.name) match {
      case None => false
      case Some(h) => h.value.startsWith("text")
    }
  }

  def bodySize(msg: HttpMessage) : Long = {
    header(msg, HttpHeaders.`Content-Length`.name) match {
      case None => msg.entity.data.length
      case Some(h) => h.value.toLong
    }
  }
}
