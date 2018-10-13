package blended.streams

import akka.NotUsed
import akka.stream.scaladsl.Flow
import blended.streams.message.FlowEnvelope
import blended.util.logging.LogLevel.LogLevel
import blended.util.logging.Logger

import scala.util.{Failure, Success, Try}

object FlowProcessor {

  type IntegrationStep = FlowEnvelope => Try[Seq[FlowEnvelope]]
  type IntegrationFlow = Flow[FlowEnvelope, FlowEnvelope, NotUsed]

  def fromFunction(name: String)(f: IntegrationStep)(implicit log: Logger): IntegrationFlow = {

    val applyFunction: Flow[FlowEnvelope, Seq[FlowEnvelope], NotUsed] = Flow.fromFunction[FlowEnvelope, Seq[FlowEnvelope]] { env: FlowEnvelope =>
      f(env) match {
        case Success(l) => l match {
          case Seq() => l
          case r => l.take(l.size - 1).map(_.withRequiresAcknowledge(false)) ++ l.takeRight(1).map(_.withRequiresAcknowledge(true))
        }

        case Failure(t) =>
          log.warn(s"Exception in FlowProcessor [$name] for message [${env.flowMessage}] : [${t.getClass().getSimpleName()} - ${t.getMessage()}]")
          Seq(env.withException(t))
      }
    }

    applyFunction.mapConcat(_.toList)
  }


  def log(level: LogLevel, name : String)(implicit log : Logger) : IntegrationFlow = fromFunction(name) { env =>
    log.log(level, s"$name : ${env.flowMessage}")
    Success(List(env))
  }

  def ack(name : String)(implicit log : Logger) : IntegrationFlow = fromFunction(name) { env =>
    env.exception match {
      case Some(t) =>
        Failure(t)
      case None =>
        env.acknowledge()
        Success(List(env))
    }
  }
}
