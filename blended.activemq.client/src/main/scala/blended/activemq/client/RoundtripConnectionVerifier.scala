package blended.activemq.client

import java.util.UUID

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.{ActorMaterializer, Materializer}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.jms.{JmsEnvelopeHeader, JmsProducerSettings, JmsStreamSupport, MessageDestinationResolver}
import blended.streams.message.FlowEnvelope
import blended.streams.processor.Collector
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class RoundtripConnectionVerifier(
  probeMsg : () => FlowEnvelope,
  verify : FlowEnvelope => Boolean,
  requestDest : JmsDestination,
  responseDest : JmsDestination,
  headerConfig : FlowHeaderConfig,
  retryInterval : FiniteDuration = 1.second,
  receiveTimeout : FiniteDuration = 250.millis
)(implicit system : ActorSystem) extends ConnectionVerifier
  with JmsStreamSupport
  with JmsEnvelopeHeader {

  private val log : Logger = Logger[RoundtripConnectionVerifier]
  private val verified : Promise[Boolean] = Promise[Boolean]()

  override def verifyConnection(cf: IdAwareConnectionFactory)(implicit eCtxt: ExecutionContext): Future[Boolean] = {
    after[Unit](10.millis, system. scheduler) {
      Future {
        probe(cf)
      }
    }

    verified.future
  }

  private def probe(cf: IdAwareConnectionFactory) : Unit = {

    implicit val materializer : Materializer = ActorMaterializer()
    implicit val eCtxt : ExecutionContext = system.dispatcher

    val id : String = UUID.randomUUID().toString()

    val probeEnv : FlowEnvelope = probeMsg()
      .withHeader(corrIdHeader(headerConfig.prefix), id, true).get
      .withHeader(replyToHeader(headerConfig.prefix), responseDest.asString).get

    val pSettings : JmsProducerSettings = JmsProducerSettings(
      log = log,
      headerCfg = headerConfig,
      connectionFactory = cf,
      jmsDestination = Some(requestDest),
      destinationResolver = s => new MessageDestinationResolver(s)
    )

    log.info(s"Running verification probe for connection [${cf.vendor}:${cf.provider}]")

    sendMessages(pSettings, log, probeEnv) match {
      case Success(s) =>
        log.info(s"Request message sent successfully to [${requestDest.asString}]")
        s.shutdown()

        implicit val to : FiniteDuration = receiveTimeout

        val collector : Collector[FlowEnvelope] = receiveMessages(
          headerCfg = headerConfig,
          cf = cf,
          dest = responseDest,
          log = log,
          listener = 1,
          selector = Some(s"JMSCorrelationID='$id'")
        )

        collector.result.onComplete {
          case Success(l) => l match {
            case Nil =>
              log.debug(s"No response received to verify connection [${cf.vendor}:${cf.provider}]")
              scheduleRetry(cf)
            case h :: _ =>
              val result : Boolean = verify(h)
              log.info(s"Verification result for client connection [${cf.vendor}:${cf.provider}] is [$result]")
              verified.complete(Success(result))
          }

          case Failure(t) =>
            log.debug(s"Failed to receive verification response to verify connection [${cf.vendor}:${cf.provider}] : [${t.getMessage()}]")
            scheduleRetry(cf)
        }

      case Failure(t) =>
        log.debug(s"Failed to send verification request to verify connection [${cf.vendor}:${cf.provider}] : [${t.getMessage()}]")
        scheduleRetry(cf)
    }
  }

  private def scheduleRetry(cf: IdAwareConnectionFactory) : Unit = {

    implicit val eCtxt : ExecutionContext = system.dispatcher

    after[Unit](retryInterval, system.scheduler){
      Future { probe(cf) }
    }
  }
}
