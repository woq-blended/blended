package blended.streams

import akka.NotUsed
import akka.stream.{FlowShape, Graph}
import akka.stream.scaladsl.Flow
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.util.{Failure, Success, Try}

object FlowProcessor {

  type IntegrationStep = FlowEnvelope => Try[Seq[FlowEnvelope]]

  private val log = Logger[FlowProcessor]

  def fromFunction(name: String)(f: IntegrationStep) : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    val applyFunction: Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

      val fun = Flow.fromFunction[FlowEnvelope, Seq[FlowEnvelope]] { env: FlowEnvelope =>
        val start = System.currentTimeMillis()

        val result = f(env) match {
          case Success(l) => l match {
            case Seq() => l
            case r => l.take(l.size - 1).map(_.withRequiresAcknowledge(false)) ++ l.takeRight(1).map(_.withRequiresAcknowledge(true))
          }

          case Failure(t) =>
            log.warn(s"Exception in FlowProcessor [$name] for message [${env.flowMessage}] : [${t.getClass().getSimpleName()} - ${t.getMessage()}]")
            Seq(env.withException(t))
        }

        log.debug(s"Integration step [$name] completed in [${System.currentTimeMillis() - start}]ms")
        result
      }

      fun.mapConcat(_.toList)
    }

    applyFunction
  }
}

trait FlowProcessor {
  val name : String
  val f : FlowProcessor.IntegrationStep

  def flow : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = FlowProcessor.fromFunction(name)(f)
}
