package de.woq.blended.jolokia

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import akka.pattern.pipe
import spray.client.pipelining._
import spray.http.HttpRequest
import scala.collection.convert.Wrappers._

import de.woq.blended.jolokia.model._
import de.woq.blended.jolokia.protocol._

import spray.json._

import scala.concurrent.Future

trait JolokiaAddress {
  val jolokiaUrl = "http://127.0.0.1:7777/jolokia"
  val user : Option[String] = None
  val password : Option[String] = None
}

class JolokiaClient extends Actor with ActorLogging { this : JolokiaAddress =>

  implicit val eCtxt = context.dispatcher

  def receive = LoggingReceive {
    case GetJolokiaVersion => jolokiaGet("version"){ JolokiaVersion(_) }
    case SearchJolokia(p) => jolokiaGet(s"search/$p"){ JolokiaSearchResult(_) }
    case ReadJolokiaMBean(name) => jolokiaGet(s"read/${name}"){ JolokiaReadResult(_) }
  }

  private def jolokiaGet[T](operation: String)(extract : JsValue => T) {
    val pipeline : HttpRequest => Future[String] = {
      sendReceive ~> unmarshal[String]
    }

    (pipeline { Get( s"${jolokiaUrl}/${operation}") }).mapTo[String].map{
      result : String => {
        val parsed = result.parseJson
        log debug s"\n${parsed.prettyPrint}"
        extract(parsed)
      }
    }.pipeTo(sender)
  }
}

object JolokiaClient {
  def apply() = new JolokiaClient with JolokiaAddress
}
