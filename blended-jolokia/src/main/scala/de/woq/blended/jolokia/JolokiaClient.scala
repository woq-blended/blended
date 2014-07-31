package de.woq.blended.jolokia

import akka.actor.{ActorRef, Actor, ActorLogging}
import akka.event.LoggingReceive
import akka.pattern.pipe
import spray.client.pipelining._
import spray.http.{BasicHttpCredentials, HttpRequest}
import scala.collection.convert.Wrappers._

import de.woq.blended.jolokia.model._
import de.woq.blended.jolokia.protocol._

import spray.json._

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait JolokiaAddress {
  val jolokiaUrl = "http://127.0.0.1:7777/jolokia"
  val user : Option[String] = None
  val password : Option[String] = None
}

class JolokiaClient extends Actor with ActorLogging { this : JolokiaAddress =>

  implicit val eCtxt = context.dispatcher

  def receive = LoggingReceive {
    case GetJolokiaVersion => jolokiaGet(sender, "version"){ JolokiaVersion(_) }
    case SearchJolokia(p) => jolokiaGet(sender, s"search/$p"){ JolokiaSearchResult(_) }
    case ReadJolokiaMBean(name) => jolokiaGet(sender, s"read/${name}"){ JolokiaReadResult(_) }
  }

  private def jolokiaGet[T](requestor: ActorRef, operation: String)(extract : JsValue => T) {

    val pipeline : HttpRequest => Future[String] = (
      (if (user.isDefined && password.isDefined)
        addCredentials(BasicHttpCredentials(user.get, password.get))
      else
        addHeader("X-Blended", "jolokia"))
      ~> sendReceive
      ~> unmarshal[String]
    )

    val response = pipeline { Get( s"${jolokiaUrl}/${operation}") }

    response.onComplete {
      case Success(result) => {
        val parsed = result.parseJson
        log debug s"\n${parsed.prettyPrint}"
        requestor ! extract(parsed)
      }
      case Failure(error) => requestor ! Failure(error)
    }
  }
}

