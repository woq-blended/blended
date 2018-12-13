package blended.jolokia

import java.net.URI

import blended.jolokia.model.{JolokiaExecResult, JolokiaReadResult, JolokiaSearchResult, JolokiaVersion}
import blended.jolokia.protocol.{MBeanSearchDef, OperationExecDef}
import blended.util.logging.Logger
import com.softwaremill.sttp._
import spray.json._

import scala.util._

case class JolokiaAddress(
  jolokiaUrl : String = "http://127.0.0.1:7777/jolokia",
  user: Option[String] = None,
  password: Option[String] = None
)

class JolokiaClient(address : JolokiaAddress) {

  private[this] implicit val backend = HttpURLConnectionBackend()
  private[this] val log = Logger[JolokiaClient]

  def version : Try[JolokiaVersion] = performGet("version").map(JolokiaVersion(_))

  def search(searchDef : MBeanSearchDef) : Try[JolokiaSearchResult] = {
    val op : String = URI.create(s"search/${searchDef.jmxDomain}:${searchDef.pattern}*".replaceAll("\"", "%22")).toString
    performGet(op).map(JolokiaSearchResult(_))
  }

  def read(name: String) : Try[JolokiaReadResult] = {
    val op : String = "read/" + URI.create(name.replaceAll("\"", "%22")).toString
    performGet(op).map(JolokiaReadResult(_))
  }

  def exec(execDef: OperationExecDef) : Try[JolokiaExecResult] = {
    val op : String = s"exec/${execDef.pattern}"
    performGet(op).map(JolokiaExecResult(_))
  }

  private def performGet(operation : String) : Try[JsValue] = Try {
    log.trace(s"performing Jolokia Get [$operation]")

    val httpClient = (address.user, address.password) match {
      case (Some(u), Some(p)) => sttp.auth.basic(u, p)
      case (_, _) => sttp.header("X-Blended", "jolokia")
    }

    val request = httpClient.get(Uri(new URI(s"${address.jolokiaUrl}/$operation")))

    val response = request.send()

    response.code match {
      case StatusCodes.Ok =>
        response.body match {
          case Left(e) => throw new Exception(e)
          case Right(json) => json.parseJson
        }
      case c => throw new Exception(s"Jolokia failed with status code [$c]")
    }
  }
}
