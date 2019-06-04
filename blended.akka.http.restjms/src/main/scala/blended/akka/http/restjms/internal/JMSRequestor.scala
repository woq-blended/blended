package blended.akka.http.restjms.internal

import java.util.concurrent.atomic.AtomicLong

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import blended.util.logging.Logger
import org.apache.camel.impl.{DefaultExchange, DefaultMessage}
import org.apache.camel.{CamelContext, Exchange, ExchangePattern}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait JMSRequestor {

  private[this] val log = Logger[JMSRequestor]
  private[this] val defaultContentTypes = List("application/json", "text/xml")
  private[this] val opCounter : AtomicLong = new AtomicLong(0l)

  implicit val eCtxt : ExecutionContext
  implicit val materializer : ActorMaterializer

  val operations : Map[String, JmsOperationConfig]
  val camelContext : CamelContext

  val httpRoute : Route = {
    path(RemainingPath) { path =>
      post {
        entity(as[HttpRequest]) { request =>
          log.debug(s"Http operation request received at [$path] : $request")

          val cType = request.entity.contentType

          operations.get(path.toString()) match {
            case None =>
              log.warn(s"Http operation at [$path] is not configured.")

              complete(
                HttpResponse(
                  status = StatusCodes.NotFound,
                  entity = HttpEntity.Strict(cType, ByteString("")),
                  headers = request.headers
                )
              )

            case Some(opCfg) =>
              val validContentTypes = opCfg.contentTypes match {
                case None    => defaultContentTypes
                case Some(l) => l
              }

              validContentTypes.filter(_ == cType.mediaType.value) match {
                case Nil =>
                  log.warn(s"Content-Type [${cType.value}] not supported.")

                  complete(
                    HttpResponse(
                      status = StatusCodes.InternalServerError,
                      entity = HttpEntity(cType, ByteString("")),
                      headers = request.headers
                    )
                  )

                case h :: _ =>
                  complete {
                    val f = requestReply(path.toString(), opCfg, cType, request)

                    f.onComplete {
                      r => log.debug(s"HttpResponse is [$r]")
                    }

                    f
                  }
              }
          }
        }
      }
    }
  }

  private[this] def executeCamel(operation : String, opCfg : JmsOperationConfig, cType : ContentType, content : Array[Byte]) : Try[Exchange] = {

    val producer = camelContext.createProducerTemplate()
    val exchange = new DefaultExchange(camelContext)

    exchange.setPattern(if (opCfg.jmsReply) ExchangePattern.InOut else ExchangePattern.InOnly)

    val msg = new DefaultMessage()

    msg.setBody(content)
    opCfg.header.foreach { case (k, v) => msg.setHeader(k, v) }

    val requestContentType = cType.mediaType.value
    log.debug(s"Request Content Type for JMS Request is [$requestContentType]")

    msg.setHeader("Content-Type", cType.mediaType.value)
    exchange.setIn(msg)

    val baseUri = s"jms:${opCfg.destination}?jmsKeyFormatStrategy=passthrough&disableTimeToLive=true&requestTimeout=${opCfg.timeout}&replyTo=restJMS.$operation"

    val uri = (opCfg.receivetimeout > 0) match {
      case true  => baseUri + s"&receiveTimeout=${opCfg.receivetimeout}"
      case false => baseUri
    }

    log.info(s"Using request/reply uri [$uri] with content type [$requestContentType]")

    try {
      val result = producer.send(uri, exchange)
      Option(result.getException()) match {
        case None    => Success(result)
        case Some(e) => Failure(e)
      }
    } catch {
      case NonFatal(e) => Failure(e)
    } finally {
      producer.stop()
    }
  }

  private[this] def requestReply(operation : String, opCfg : JmsOperationConfig, cType : ContentType, request : HttpRequest) : Future[HttpResponse] = {

    val opNum = opCounter.incrementAndGet()
    val data = request.entity.getDataBytes().runWith(Sink.seq[ByteString], materializer)

    def filterHeaders(headers : Seq[HttpHeader]) : collection.immutable.Seq[HttpHeader] = {
      val notAllowedInResponses : Seq[String] = Seq("Host", "Accept-Encoding", "User-Agent", "Timeout-Access")
      headers.filterNot(h => notAllowedInResponses.contains(h.name())).to[collection.immutable.Seq]
    }

    data.map { result =>

      val content : Array[Byte] = result.flatten.toArray
      log.debug(s"Received request [$opNum] of length [${content.size}] encoding [${opCfg.encoding}], [${new String(content, opCfg.encoding)}]")

      executeCamel(operation, opCfg, cType, content) match {
        case Success(exchange) =>

          if (opCfg.jmsReply) {
            val body = exchange.getOut().getBody(classOf[Array[Byte]])
            log.info(s"Received response [$opNum] of length [${body.size}] encoding [${opCfg.encoding}], [${new String(body, opCfg.encoding)}]")

            HttpResponse(
              status = StatusCodes.OK,
              entity = HttpEntity.Strict(cType, ByteString(body)),
              headers = filterHeaders(request.headers)
            )
          } else {
            HttpResponse(
              status = if (opCfg.isSoap) StatusCodes.Accepted else StatusCodes.OK,
              entity = HttpEntity.Strict(cType, ByteString("")),
              headers = filterHeaders(request.headers)
            )
          }
        case Failure(e) =>
          log.warn(s"Error performing JMS request/reply [${e.getMessage()}]")
          HttpResponse(
            status = StatusCodes.InternalServerError,
            entity = HttpEntity.Strict(cType, ByteString("")),
            headers = filterHeaders(request.headers)
          )
      }
    }
  }
}

