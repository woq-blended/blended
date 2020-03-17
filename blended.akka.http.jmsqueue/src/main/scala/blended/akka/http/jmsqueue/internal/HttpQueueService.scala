package blended.akka.http.jmsqueue.internal

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import blended.util.logging.Logger
import javax.jms._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait HttpQueueService {

  implicit val eCtxt : ExecutionContext
  val qConfig : HttpQueueConfig
  def withConnectionFactory[T](vendor : String, provider : String)(f : Option[ConnectionFactory] => T) : T

  private[this] val log = Logger[HttpQueueService]

  private case class ReceiveResult(vendor : String, provider : String, queue : String, msg : Try[Option[Message]])

  private[this] def propsToHeaders(m : Message) : List[HttpHeader] = m.getPropertyNames().asScala.map { key =>
    val realKey = key.toString()
    val realValue = Option(m.getObjectProperty(realKey)).map(_.toString()).getOrElse("")
    RawHeader(realKey, realValue)
  }.toList

  private[this] def messageToResponse(result : ReceiveResult) : HttpResponse = {

    val response = result.msg match {
      case Failure(_) =>
        HttpResponse(StatusCodes.InternalServerError)
      case Success(msg) =>
        msg match {
          case None =>
            HttpResponse(StatusCodes.NoContent)
          case Some(m) if m.isInstanceOf[TextMessage] =>
            HttpResponse(
              status = StatusCodes.OK,
              headers = propsToHeaders(m),
              entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, m.asInstanceOf[TextMessage].getText())
            )
          case Some(m) if m.isInstanceOf[BytesMessage] =>

            val bMsg = m.asInstanceOf[BytesMessage]

            val body = new Array[Byte](bMsg.getBodyLength().toInt)
            bMsg.readBytes(body)

            HttpResponse(
              status = StatusCodes.OK,
              headers = propsToHeaders(m),
              entity = HttpEntity(ContentTypes.`application/octet-stream`, body)
            )
          case Some(_) =>
            log.debug(s"Message received on queue [${result.queue}] for provider [${result.vendor}:${result.provider}] is not a text or binary message. Message discarded ...")
            HttpResponse(StatusCodes.InternalServerError)
        }
    }

    log.debug(s"Http Response is [$response]")
    response
  }

  private[this] def performReceive(
    vendor : String,
    provider : String,
    queue : String
  ) : ConnectionFactory => ReceiveResult = { cf =>

    var conn : Option[Connection] = None
    var sess : Option[Session] = None
    var consumer : Option[MessageConsumer] = None

    try {
      conn = Some(cf.createConnection())
      conn.get.start()
      sess = Some(conn.get.createSession(false, Session.CLIENT_ACKNOWLEDGE))
      consumer = Some(sess.get.createConsumer(sess.get.createQueue(queue)))

      Option(consumer.get.receive(100)) match {
        case None =>
          log.debug(s"No message available on queue [$queue] for [$vendor:$provider]")
          ReceiveResult(vendor, provider, queue, Success(None))
        case Some(m) =>
          log.debug(s"Received message from queue [$queue] for [$vendor:$provider] : [$m]")
          m.acknowledge()
          ReceiveResult(vendor, provider, queue, Success(Some(m)))
      }

    } catch {
      case NonFatal(e) =>
        log.debug(s"Error receiving message from queue [$queue] for [$vendor:$provider]. Cause: ${e.getMessage()}")
        ReceiveResult(vendor, provider, queue, Failure(e))
    } finally {
      Future {
        blocking {
          try {
            consumer.foreach(_.close())
          } catch {
            case NonFatal(t) => log.debug(s"Error closing consumer [${t.getMessage()}]")
          }

          try {
            sess.foreach(_.close())
          } catch {
            case NonFatal(t) => log.debug(s"Error closing session [${t.getMessage()}]")
          }

          try {
            conn.foreach(_.close())
          } catch {
            case NonFatal(t) => log.debug(s"Error closing connection [${t.getMessage()}]")
          }
        }
      }
    }
  }

  private[this] def receive(vendor : String, provider : String, queue : String) : HttpResponse = {

    withConnectionFactory[HttpResponse](vendor, provider) {
      case None =>
        val msg = s"No connection factory found for [$vendor:$provider]"
        log.warn(msg)
        messageToResponse(ReceiveResult(vendor, provider, queue, Failure(new Exception(msg))))
      case Some(cf) =>
        messageToResponse(performReceive(vendor, provider, queue)(cf))
    }
  }

  val httpRoute : Route = {
    path(Segments) { path =>
      get {
        entity(as[HttpRequest]) { request =>
          log.debug(s"Http operation request received at [$path] : $request")

          path match {
            case qPath :: queue :: Nil =>
              qConfig.httpQueues.find { case ((v, p), c) => c.path == qPath } match {
                case None =>
                  log.warn(s"No queues configured for path [$qPath].")
                  complete(StatusCodes.Unauthorized)
                case Some(((v, p), c)) =>
                  if (c.queueNames.contains(queue)) {
                    complete(receive(v, p, queue))
                  } else {
                    log.warn(s"Queue [$queue] is not configured for [$v:$p] at path [$qPath]")
                    complete(StatusCodes.Unauthorized)
                  }
              }
            case _ =>
              log.warn(s"Request [${path.mkString("/")}] does not match the form 'path/queue'")
              complete(StatusCodes.BadRequest)
          }
        }
      }
    }
  }
}

