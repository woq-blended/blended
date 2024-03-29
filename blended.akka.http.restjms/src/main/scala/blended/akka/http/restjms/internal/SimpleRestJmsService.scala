package blended.akka.http.restjms.internal
import java.util.UUID
import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.OverflowStrategy
import akka.util.ByteString
import blended.akka.OSGIActorConfig
import blended.jms.bridge.BridgeProviderRegistry
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.jms._
import blended.streams.message.{BinaryFlowMessage, FlowEnvelope, FlowEnvelopeLogger, FlowMessage, TextFlowMessage}
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig, FlowProcessor, StreamController, StreamFactories}
import blended.util.logging.{LogLevel, Logger}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

class SimpleRestJmsService(
  name : String,
  osgiCfg : OSGIActorConfig,
  streamsConfig :BlendedStreamsConfig,
  registry: BridgeProviderRegistry,
  cf : IdAwareConnectionFactory
) extends JmsEnvelopeHeader {

  private val operationHeader = "RESTOperation"

  private implicit val system : ActorSystem = osgiCfg.system
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private val log : Logger = Logger(s"${getClass().getName()}.$name")

  private val internalProvider = registry.internalProvider.get
  private val internalCfg = registry.jmsProvider(internalProvider.vendor, internalProvider.provider).get

  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(osgiCfg.ctContext)
  private val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerCfg, log)

  private val idSeparator : String = "##"

  private val defaultContentTypes = List("application/json", "text/xml")
  private val restConfig : RestJMSConfig = RestJMSConfig.fromConfig(osgiCfg.config)
  private val responseDestination : JmsDestination = JmsQueue(s"restJMS.$name.response")

  private val operations : Map[String, JmsOperationConfig] = restConfig.operations
  log.info(s"Starting RestJMS Service with [$operations]")

  private val wiretapJms = new JmsStreamSupport {}
  private val wiretapSettings : JmsProducerSettings = JmsProducerSettings(
    log = envLogger,
    headerCfg = headerCfg,
    connectionFactory = cf,
    destinationResolver = new MessageDestinationResolver(_),
    keyFormatStrategy = new PassThroughKeyFormatStrategy()
  )

  private val pendingRequests : mutable.Map[String, (HttpRequest, Promise[HttpResponse])] = mutable.Map.empty

  private val producerSettings : JmsProducerSettings = JmsProducerSettings(
    log = envLogger,
    headerCfg = headerCfg,
    connectionFactory = cf,
    destinationResolver = s => new MessageDestinationResolver(s),
    keyFormatStrategy = new PassThroughKeyFormatStrategy()
  )

  private val consumerSettings : JmsConsumerSettings = JmsConsumerSettings(
    log = envLogger,
    headerCfg = headerCfg,
    connectionFactory = cf,
    jmsDestination = Some(responseDestination),
    logLevel = _ => LogLevel.Debug,
    // We use the real JMSCorrelation Id here, not the one we keep in our ap properties
    selector = Some(s"${corrIdHeader("")} LIKE '${osgiCfg.ctContext.uuid}%'"),
    keyFormatStrategy = new PassThroughKeyFormatStrategy(),
    ackTimeout = 1.second
  )

  private def sendWiretap(env: FlowEnvelope, isRequest: Boolean) : FlowEnvelope = {

    env.header[String](operationHeader).foreach{ opName =>
      val opCfg = restConfig.operations(opName)
      val resType : Option[String] = if (isRequest) opCfg.reqResourceType else opCfg.respResourceType

      resType.foreach{ resType =>
        log.info(s"Sending wiretap [${env.id}] [${if (isRequest) "request" else "response"}] to dispatcher with [$resType]")

        val wiretapMsg = env
          .withHeader(headerCfg.headerResourceType, resType).get
          .withHeader(headerCfg.headerBridgeVendor, internalCfg.vendor).get
          .withHeader(headerCfg.headerBridgeProvider, internalCfg.provider).get
          .withHeader(destHeader(headerCfg.prefix), internalCfg.inbound.asString).get
          .removeHeader(replyToHeader(headerCfg.prefix))

        val toSend = opCfg.wiretapTtl.fold(
          wiretapMsg.removeHeader(expireHeader(headerCfg.prefix))
        )(
          ttl => wiretapMsg.withHeader(expireHeader(headerCfg.prefix), System.currentTimeMillis() + ttl.toMillis).get
        )

        log.debug(s"Wiretap message : $toSend")

        wiretapJms.sendMessages(wiretapSettings, envLogger,  10.seconds, toSend)
      }
    }

    env
  }

  private val sendToJms : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(new JmsProducerStage(s"$name-send", producerSettings))

  private val responseSrc : Source[FlowEnvelope, NotUsed] =
    Source.fromGraph(new JmsConsumerStage(s"$name-response", consumerSettings))
    .via(FlowProcessor.fromFunction("handleResponse", envLogger){ env => Try {
      val corrId : String = env.headerWithDefault(corrIdHeader(""), env.id)
      val responseEnv : FlowEnvelope = if (corrId.split(idSeparator).size == 2) {
        FlowEnvelope(env.flowMessage, corrId.split(idSeparator).toSeq.last)
      } else {
        env
      }
      handleResponse(responseEnv)
      responseEnv
    }})

  private val requestSrc : Source[FlowEnvelope, ActorRef] =
    StreamFactories.actorSource[FlowEnvelope](100, OverflowStrategy.dropNew)
      .via(sendToJms)
      .via(FlowProcessor.fromFunction(s"$name-wiretap", envLogger) { env =>
        Try { sendWiretap(env, true) }
      })

  private var requestActor : Option[ActorRef] = None

  private var requestStream : Option[ActorRef] = None
  private var responseStream : Option[ActorRef] = None

  def start() : Unit = {
    requestStream = Some(system.actorOf(StreamController.props[FlowEnvelope, ActorRef](s"$name-send", requestSrc, streamsConfig){ a =>
      log.debug(s"Stream actor [$name] has been started : [$a]")
      osgiCfg.system.actorOf(ActorWatcher.props(a))
      requestActor = Some(a)
    }))
    responseStream = Some(system.actorOf(StreamController.props[FlowEnvelope, NotUsed](s"$name-response", responseSrc, streamsConfig)(_ => {} )))
  }

  def stop() : Unit = {
    requestStream.foreach( _ ! StreamController.Stop)
    responseStream.foreach(_ ! StreamController.Stop)
  }

  val httpRoute : Route = {
    path(RemainingPath) { path =>
      post {
        entity(as[HttpRequest]) { request =>
          log.info(s"Http operation request received at [$path] with headers [${request.headers}]")

          requestActor match {
            case None =>
              val msg : String = "JMS request reply stream is not ready"
              log.warn(msg)
              complete(HttpResponse(
                status = StatusCodes.InternalServerError,
                entity = HttpEntity.Strict(request.entity.contentType, ByteString(msg)),
                headers = filterHeaders(request.headers)
              ))
            case Some(a) =>
              val f : Future[HttpResponse] = performRequest(path.toString(), request, a)

              f.onComplete { r =>
                log.info(s"Http response is [$r]")
              }

              complete(f)
          }
        }
      }
    }
  }

  private def filterHeaders(headers : Seq[HttpHeader]) : collection.immutable.Seq[HttpHeader] = {
    val notAllowedInResponses : Seq[String] = Seq("Host", "Accept-Encoding", "User-Agent", "Timeout-Access")
    headers.filterNot(h => notAllowedInResponses.contains(h.name())).toList
  }

  private def performRequest(opKey : String, request : HttpRequest, actor : ActorRef): Future[HttpResponse] = {
    operations.get(opKey) match {
      case None =>
        log.warn(s"Http operation [$opKey] is not configured.")

        Future(HttpResponse(
          status = StatusCodes.NotFound,
          entity = HttpEntity.Strict(request.entity.contentType, ByteString.empty),
          headers = filterHeaders(request.headers)
        ))

      case Some(opCfg) =>
        val cType : ContentType = request.entity.contentType

        val validContentTypes = opCfg.contentTypes match {
          case None    => defaultContentTypes
          case Some(l) => l
        }

        validContentTypes.filter(_ == cType.mediaType.value) match {
          case Nil =>
            log.warn(s"Content-Type [${cType.value}] not supported.")

            Future(HttpResponse(
              status = StatusCodes.InternalServerError,
              entity = HttpEntity(cType, ByteString.empty),
              headers = filterHeaders(request.headers)
            ))

          case _ :: _ =>
            requestReply(opKey, opCfg, cType, request, actor)
        }
    }
  }

  private def requestReply(operation : String, opCfg : JmsOperationConfig, cType : ContentType, request : HttpRequest, actor : ActorRef) : Future[HttpResponse] = {

    val data : Future[Seq[ByteString]] = request.entity.getDataBytes().asScala.runWith(Sink.seq[ByteString])

    data.map { result =>
      val content: Array[Byte] = result.flatten.toArray

      val envId : String = UUID.randomUUID().toString()

      val header : Seq[(String, Any)] = Seq(
        operationHeader -> operation,
        destHeader(headerCfg.prefix) -> opCfg.destination,
        replyToHeader(headerCfg.prefix) -> s"${responseDestination.name}",
        corrIdHeader(headerCfg.prefix) -> s"${osgiCfg.ctContext.uuid}##$envId",
        "Content-Type" -> cType.mediaType.value
      ) ++ opCfg.header.map{ case (k,v) => k -> v }

      val env: FlowEnvelope = FlowEnvelope(FlowMessage(content)(FlowMessage.props(header:_*).get), envId)

      log.debug(s"Received request [${env.id}] of length [${content.length}], encoding [${opCfg.encoding}], content type [${cType.mediaType}]")
      log.debug(s"Request envelope is [$env]")

      val response : Future[HttpResponse] = addRequest(env.id, request)
      system.scheduler.scheduleOnce(opCfg.timeout.millis){
        timeoutRequest(env.id, opCfg.timeout.millis)
      }
      actor ! env

      checkComplete(env.id, opCfg)

      response
    }.flatten
  }

  private def checkComplete(id : String, opCfg : JmsOperationConfig) : Unit = synchronized {
    pendingRequests.get(id) match {
      case Some((req, p)) =>
        if (!opCfg.jmsReply) {
          p.complete(Success(HttpResponse(
            status = if (opCfg.isSoap) StatusCodes.Accepted else StatusCodes.OK,
            entity = HttpEntity(req.entity.contentType, ByteString.empty),
            headers = filterHeaders(req.headers)
          )))
        }
      case None =>
    }
  }

  private def addRequest(id : String, request : HttpRequest) : Future[HttpResponse] = synchronized {
    val p : Promise[HttpResponse] = Promise()

    pendingRequests += id -> (request -> p)

    p.future.onComplete{ _ =>
      log.debug(s"Cleaning up Http request [$id]")
      removeRequest(id)
    }

    p.future
  }

  private def removeRequest(id : String) : Unit = synchronized {
    pendingRequests -= id
  }

  private def timeoutRequest(id : String, timeout: FiniteDuration) = synchronized{
    pendingRequests.get(id) match {
      case Some((req, p)) =>
        log.warn(s"Request [$id] has timed out after [$timeout]")
        p.complete(Success(HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = HttpEntity(req.entity.contentType, ByteString.empty),
          headers = filterHeaders(req.headers)
        )))
      case None =>
    }
  }

  private def handleResponse(env : FlowEnvelope) : Unit = synchronized {

    def createEntity(req : HttpRequest, msg : FlowMessage) : ResponseEntity = msg match {
      case tMsg: TextFlowMessage => HttpEntity(req.entity.contentType, tMsg.getText().getBytes())
      case bMsg: BinaryFlowMessage => HttpEntity(req.entity.contentType, bMsg.getBytes())
      case _ => HttpEntity(req.entity.contentType, ByteString.empty)
    }

    pendingRequests.get(env.id) match {
      case Some((req, p)) =>
        log.info(s"Received response for HTTP request [$env]")
        sendWiretap(env, isRequest = false)
        env.exception match {
          case Some(t) =>
            log.warn(t)(t.getMessage())
            p.complete(Failure(t))
          case None =>
            p.complete(Success(HttpResponse(
              StatusCodes.OK,
              entity = createEntity(req, env.flowMessage),
              headers = filterHeaders(req.headers)
            )))
        }
      case None =>
        log.warn(s"No pending request for received response [$env]")
    }
  }

  private object ActorWatcher {
    def props(watched : ActorRef) : Props =  Props(new ActorWatcher(watched))
  }

  private class ActorWatcher(watched: ActorRef) extends Actor {

    override def preStart(): Unit = context.watch(watched)

    override def receive: Receive = {
      case Terminated(a) =>
        log.info(s"Actor [$a] has terminated")
    }
  }
}

