package blended.activemq.client

import java.util.UUID

import akka.actor.ActorSystem
import akka.pattern.after
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.FlowHeaderConfig
import blended.streams.jms.{JmsEnvelopeHeader, JmsProducerSettings, JmsStreamSupport, MessageDestinationResolver}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.processor.Collector
import blended.util.logging.{LogLevel, Logger}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class RoundtripConnectionVerifier(
  probeMsg : String => FlowEnvelope,
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
  private val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerConfig, log)
  private val verified : Promise[Boolean] = Promise[Boolean]()

  override def verifyConnection(cf : IdAwareConnectionFactory)(implicit eCtxt : ExecutionContext) : Future[Boolean] = {
    after[Unit](10.millis, system.scheduler) {
      Future {
        probe(cf)
      }
    }

    verified.future
  }

  private def probe(cf : IdAwareConnectionFactory) : Unit = {

    implicit val eCtxt : ExecutionContext = system.dispatcher

    val id : String = UUID.randomUUID().toString()

    val probeEnv : FlowEnvelope = probeMsg(id)
      .withHeader(corrIdHeader(headerConfig.prefix), id, true).get
      .withHeader(replyToHeader(headerConfig.prefix), responseDest.asString).get

    val pSettings : JmsProducerSettings = JmsProducerSettings(
      log = envLogger,
      headerCfg = headerConfig,
      connectionFactory = cf,
      jmsDestination = Some(requestDest),
      timeToLive = Some(receiveTimeout * 2),
      destinationResolver = s => new MessageDestinationResolver(s),
      logLevel = _ => LogLevel.Debug
    )

    log.info(s"Running verification probe for connection [${cf.vendor}:${cf.provider}]")

    sendMessages(pSettings, envLogger, probeEnv) match {
      case Success(s) =>
        log.info(s"Request message sent successfully to [${requestDest.asString}] : [$probeEnv]")
        s.shutdown()

        val collector : Collector[FlowEnvelope] = receiveMessages(
          headerCfg = headerConfig,
          cf = cf,
          dest = responseDest,
          log = envLogger,
          listener = 1,
          selector = Some(s"JMSCorrelationID='$id'"),
          timeout = Some(receiveTimeout)
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

  private def scheduleRetry(cf : IdAwareConnectionFactory) : Unit = {

    implicit val eCtxt : ExecutionContext = system.dispatcher

    after[Unit](retryInterval, system.scheduler) {
      Future { probe(cf) }
    }
  }
}
