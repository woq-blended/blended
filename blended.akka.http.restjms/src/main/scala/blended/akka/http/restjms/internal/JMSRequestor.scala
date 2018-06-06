package blended.akka.http.restjms.internal

import java.util.concurrent.atomic.AtomicLong

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.ByteString
import org.apache.camel.{CamelContext, ExchangePattern}
import org.apache.camel.impl.{DefaultExchange, DefaultMessage}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

trait JMSRequestor {

  private[this] val log = LoggerFactory.getLogger(classOf[JMSRequestor])
  private[this] val defaultContentTypes = List("application/json", "text/xml")
  private[this] val opCounter : AtomicLong = new AtomicLong(0l)

  implicit val materializer : ActorMaterializer
  implicit val eCtxt : ExecutionContext

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
                case None => defaultContentTypes
                case Some(l) => l
              }

              validContentTypes.filter(_ == cType.value.split(";").head) match {
                case Nil =>
                  log.warn(s"Content-Type [${cType.value}] not supported.")

                  complete(
                    HttpResponse(
                      status = StatusCodes.InternalServerError,
                      entity = HttpEntity(cType, ByteString("")),
                      headers = request.headers
                    )
                  )

                case _ =>

                  val opNum = opCounter.incrementAndGet()

                  val producer = camelContext.createProducerTemplate()
                  val exchange = new DefaultExchange(camelContext)

                  exchange.setPattern(if (opCfg.jmsReply) ExchangePattern.InOut else ExchangePattern.InOnly)

                  val msg = new DefaultMessage()

                  // TODO : can this be done in a better way ?
                  val bytes = request.entity.toStrict(1.second)
                  val content : Array[Byte] = Await.result(bytes, 3.seconds).getData().toArray
                  // << TODO

                  if (log.isDebugEnabled()) {
                    log.debug(s"Received request [$opNum] of length [${content.size}] encoding [${opCfg.encoding}], [${new String(content, opCfg.encoding)}]")
                  }

                  msg.setBody(content)
                  opCfg.header.foreach { case (k,v) => msg.setHeader(k, v) }

                  msg.setHeader("Content-Type", cType)
                  exchange.setIn(msg)

                  val baseUri = s"jms:${opCfg.destination}?jmsKeyFormatStrategy=passthrough&disableTimeToLive=true&requestTimeout=${opCfg.timeout}&replyTo=restJMS.$path"

                  val uri = (opCfg.receivetimeout > 0) match {
                    case true => baseUri + s"&receiveTimeout=${opCfg.receivetimeout}"
                    case false => baseUri
                  }

                  log.debug(s"Using request/reply uri [$uri]")

                  try {
                    Option(producer.send(uri, exchange).getException()) match {
                      case None =>
                        if (opCfg.jmsReply) {

                          val result = exchange.getOut().getBody(classOf[Array[Byte]])

                          if (log.isDebugEnabled()) {
                            log.debug(s"Received response [$opNum] of length [${result.size}] encoding [${opCfg.encoding}], [${new String(result, opCfg.encoding)}]")
                          }

                          complete(
                            HttpResponse(
                              status = StatusCodes.OK,
                              entity = HttpEntity.Strict(cType, ByteString(result)),
                              headers = request.headers
                            )
                          )
                        } else {
                          complete(
                            HttpResponse(
                              status = if (opCfg.isSoap) StatusCodes.Accepted else StatusCodes.OK,
                              entity = HttpEntity(cType, ByteString("")),
                              headers = request.headers
                            )
                          )
                        }
                      case Some(e) =>
                        log.warn("Error performing JMS request/reply", e.getMessage())
                        complete(
                          HttpResponse(
                            status = StatusCodes.InternalServerError,
                            entity = HttpEntity(cType, ByteString("")),
                            headers = request.headers
                          )
                        )
                    }
                  } finally {
                    producer.stop()
                  }
              }
           }
        }
      }
    }
  }
}

