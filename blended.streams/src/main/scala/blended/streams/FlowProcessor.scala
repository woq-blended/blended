package blended.streams

import akka.NotUsed
import akka.stream.scaladsl.Flow
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.util.{Failure, Success, Try}

object FlowProcessor {

  type IntegrationStep = FlowEnvelope => Try[FlowEnvelope]
  type IntegrationFlow = Flow[FlowEnvelope, FlowEnvelope, NotUsed]

  def fromFunction(name: String)(f : IntegrationStep)(implicit log : Logger) : IntegrationFlow = {

    val checkException : FlowEnvelope => FlowEnvelope = { env =>
      env.exception match {
        case None => f(env) match {
          case Success(r) => r
          case Failure(t) =>
            log.error(t)(s"Exception in integration step")
            env.withException(t)
        }
      }
    }

    Flow.fromFunction(checkException)
  }

  def log(name : String)(implicit log : Logger) : IntegrationFlow = fromFunction(name) { env =>
    log.info(s"${env.flowMessage}")
    Success(env)
  }

  def ack(name : String)(implicit log : Logger) : IntegrationFlow = fromFunction(name) { env =>
    env.exception match {
      case Some(t) =>
        Failure(t)
      case None =>
        env.acknowledge()
        Success(env)
    }
  }
}
