package blended.jolokia

import java.net.URI

import blended.util.logging.Logger
import sttp.client._
import spray.json._
import sttp.model.{Uri, StatusCode}

import scala.util._

case class JolokiaAddress(
  jolokiaUrl : String = "http://127.0.0.1:7777/jolokia",
  user : Option[String] = None,
  password : Option[String] = None
)

class JolokiaClient(address : JolokiaAddress) {

  private[this] implicit val backend = HttpURLConnectionBackend()
  private[this] val log = Logger[JolokiaClient]

  val url = address.jolokiaUrl

  def version : Try[JolokiaVersion] = performGet("version")(JolokiaVersion(_))

  def search(searchDef : MBeanSearchDef) : Try[JolokiaSearchResult] = {
    val op : String = URI.create(s"search/${searchDef.jmxDomain}:${searchDef.pattern}*".replaceAll("\"", "%22")).toString
    performGet(op)(JolokiaSearchResult(_))
  }

  def read(name : String) : Try[JolokiaReadResult] = {
    val op : String = "read/" + URI.create(name.replaceAll("\"", "%22")).toString
    performGet(op)(v => JolokiaReadResult(name, v))
  }

  def exec(execDef : OperationExecDef) : Try[JolokiaExecResult] = {
    val op : String = s"exec/${execDef.pattern}"
    performGet(op)(JolokiaExecResult(_))
  }

  private def performGet[T <: JolokiaObject](operation : String)(f : JsValue => T) : Try[T] = Try {
    log.trace(s"performing Jolokia Get [$operation]")

    val uri = Uri(new URI(s"${address.jolokiaUrl}/$operation"))

    val request = (address.user, address.password) match {
      case (Some(u), Some(p)) => basicRequest.get(uri).auth.basic(u, p)
      case (_, _)             => basicRequest.get(uri).header("X-Blended", "jolokia")
    }

    log.debug(s"Executing Jolokia Request [$request]")
    val response = request.send()

    log.debug(s"Jolokia response is [$response]")

    response.code match {
      case StatusCode.Ok =>
        response.body match {
          case Left(e) => throw new Exception(e)
          case Right(json) =>
            val result = f(json.parseJson)
            log.debug(s"Parsed Jolokia result is [$result]")
            result
        }
      case c => throw new Exception(s"Jolokia failed with status code [$c]")
    }
  }
}
