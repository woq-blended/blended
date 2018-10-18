package blended.streams

import akka.NotUsed
import akka.stream.{FlowShape, Graph}
import akka.stream.scaladsl.Flow
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object FlowProcessor {

  type IntegrationStep = FlowEnvelope => Try[FlowEnvelope]

  def transform[T](name: String, log: Logger)(f : FlowEnvelope => Try[T])(implicit clazz : ClassTag[T])
    : Graph[FlowShape[FlowEnvelope, Either[FlowEnvelope, T]], NotUsed] = {

    Flow.fromFunction[FlowEnvelope, Either[FlowEnvelope, T]] { env =>
      env.exception match {
        case None =>
          log.info(s"Starting function [${env.id}]:[$name]")
          val start = System.currentTimeMillis()
          f(env) match {
            case Success(s) =>
              log.debug(s"Function [${env.id}]:[$name] completed in [${System.currentTimeMillis() - start}]ms")
              Right(s)
            case Failure(t) =>
              log.warn(t)(s"Failed to create [${clazz.runtimeClass.getName()}] in [${env.id}]:[$name]")
              Left(env.withException(t))
          }
        case Some(t) =>
          Left(env)
        }
      }
  }

  def fromFunction(name: String, log: Logger)(f: IntegrationStep) : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env: FlowEnvelope =>

      env.exception match {
        case None =>
          log.info(s"Starting Integrationstep [${env.id}]:[$name]")
          val start = System.currentTimeMillis()

          val result = f(env) match {
            case Success(s) => s

            case Failure(t) =>
              log.warn(t)(s"Exception in FlowProcessor [${env.id}]:[$name] for message [${env.flowMessage}] : [${t.getClass().getSimpleName()} - ${t.getMessage()}]")
              env.withException(t)
          }

          log.info(s"Integration step [${env.id}]:[$name] completed in [${System.currentTimeMillis() - start}]ms")
          result
        case Some(_) =>
          log.debug(s"Skipping integration step [${env.id}]:[$name] due to exception caught in flow.")
          env
      }
    }
  }
}

trait FlowProcessor {
  val name : String
  val f : FlowProcessor.IntegrationStep

  def flow(log: Option[Logger]) : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] =
    FlowProcessor.fromFunction(
      name,
      log match {
        case Some(l) => l
        case None => Logger[FlowProcessor]
      }
    )(f)
}
