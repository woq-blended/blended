package blended.streams

import akka.NotUsed
import akka.stream.{FlowShape, Graph}
import akka.stream.scaladsl.Flow
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object FlowProcessor {

  type IntegrationStep = FlowEnvelope => Try[Seq[FlowEnvelope]]

  def transform[T](name: String, log: Logger)(f : FlowEnvelope => Try[T])(implicit clazz : ClassTag[T]) : Graph[FlowShape[FlowEnvelope, Try[T]], NotUsed] = {

    Flow.fromFunction[FlowEnvelope, Try[T]] { env =>
      Try {
        env.exception match {
          case None =>
            log.info(s"Starting function [${env.id}]:[$name]")
            val start = System.currentTimeMillis()
            f(env) match {
              case Success(s) =>
                s
              case Failure(t) =>
                log.warn(t)(s"Failed to create [${clazz.runtimeClass.getName()}] in [${env.id}]:[$name]")
                throw t
            }
          case Some(t) =>
            throw t
        }
      }
    }
  }

  def fromFunction(name: String, log: Logger)(f: IntegrationStep) : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    val applyFunction: Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

      val fun = Flow.fromFunction[FlowEnvelope, Seq[FlowEnvelope]] { env: FlowEnvelope =>

        env.exception match {
          case None =>
            log.info(s"Starting Integrationstep [${env.id}]:[$name]")
            val start = System.currentTimeMillis()

            val result = f(env) match {
              case Success(l) => l match {
                case Seq() => l
                case r if env.requiresAcknowledge => l.take(l.size - 1).map(_.withRequiresAcknowledge(false)) ++ l.takeRight(1).map(_.withRequiresAcknowledge(true))
                case l => l
              }

              case Failure(t) =>
                log.warn(t)(s"Exception in FlowProcessor [${env.id}]:[$name] for message [${env.flowMessage}] : [${t.getClass().getSimpleName()} - ${t.getMessage()}]")
                Seq(env.withException(t))
            }

            log.info(s"Integration step [${env.id}]:[$name] completed in [${System.currentTimeMillis() - start}]ms")
            result
          case Some(_) =>
            log.debug(s"Skipping integration step [${env.id}]:[$name] due to exception caught in flow.")
            Seq(env)
        }
      }

      fun.mapConcat(_.toList)
    }

    applyFunction
  }
}

trait FlowProcessor {
  val name : String
  val f : FlowProcessor.IntegrationStep

  def flow(log: Logger) : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] =
    FlowProcessor.fromFunction(name, log)(f)
}
