package blended.jolokia

import java.net.URI

import scala.util.Failure
import scala.util.Success

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import blended.jolokia.model.JolokiaExecResult
import blended.jolokia.model.JolokiaReadResult
import blended.jolokia.model.JolokiaSearchResult
import blended.jolokia.model.JolokiaVersion
import blended.jolokia.protocol.ExecJolokiaOperation
import blended.jolokia.protocol.GetJolokiaVersion
import blended.jolokia.protocol.ReadJolokiaMBean
import blended.jolokia.protocol.SearchJolokia
import spray.json.JsValue
import spray.json.enrichString
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import blended.util.logging.Logger

trait JolokiaAddress {
  val jolokiaUrl = "http://127.0.0.1:7777/jolokia"
  val user: Option[String] = None
  val password: Option[String] = None
}

class JolokiaClient extends Actor with ActorLogging { this: JolokiaAddress =>

  private[this] val log = Logger[JolokiaClient]

  implicit val eCtxt = context.dispatcher

  def receive = LoggingReceive {
    case GetJolokiaVersion => jolokiaGet(sender, "version") { JolokiaVersion(_) }
    case SearchJolokia(searchDef) =>
      val request = URI.create(s"search/${searchDef.jmxDomain}:${searchDef.pattern}*".replaceAll("\"", "%22")).toString
      log.debug(s"Jolokia search request is [$request]")
      jolokiaGet(sender, request) { JolokiaSearchResult(_) }
    case ReadJolokiaMBean(name) =>
      val request = "read/" + URI.create(name.replaceAll("\"", "%22")).toString
      log.debug(s"Jolokia read request is [$request")
      jolokiaGet(sender, request) { JolokiaReadResult(_) }
    case ExecJolokiaOperation(execDef) =>
      val request = s"exec/${execDef.pattern}"
      log.debug(s"Jolokia exec request is [$request].")
      jolokiaGet(sender, request) { JolokiaExecResult(_) }
  }

  private def jolokiaGet[T](requestor: ActorRef, operation: String)(extract: JsValue => T): Unit = {
    log.trace("jolokiaGet from " + requestor + ", operation: " + operation)

    implicit val mat: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

    val rawRequest = HttpRequest(
      uri = s"$jolokiaUrl/$operation",
      method = HttpMethods.GET
    )
    val request = (user, password) match {
      case (Some(u), Some(p)) => rawRequest.addCredentials(BasicHttpCredentials(u, p))
      case _ => rawRequest.addHeader(RawHeader("X-Blended", "jolokia"))
    }

    log.trace("request: " + request)

    val response = Http(context.system).singleRequest(request)

    response.onComplete {
      case Success(result) =>
        log.trace("response: " + result)
        result.status match {
          case StatusCodes.OK =>
            implicit val unm = Unmarshaller
              .stringUnmarshaller
              //              .forContentTypes(MediaTypes.`application/json`)
              .map(_.parseJson)
            log.trace("About to unmarshal entity: " + result.entity)
            val parsed = Unmarshal(result.entity).to[JsValue]
            parsed.onComplete {
              case Success(parsed) =>
                log.debug(s"\n${parsed.prettyPrint}")
                requestor ! extract(parsed)
              case failure =>
                log.error(failure.failed.get)("Could not unmarshal entity")
                requestor ! failure
            }

          case sc =>
            log.debug("response: " + result)
            result.discardEntityBytes()
            requestor ! Failure(new RuntimeException("HTTP result code " + sc))
        }

      case failure =>
        log.debug("response failure: " + failure)
        requestor ! failure
    }
  }
}

