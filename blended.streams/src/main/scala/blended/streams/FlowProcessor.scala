package blended.streams

import akka.NotUsed
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL}
import akka.stream.{FanOutShape2, FlowShape, Graph}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.util.logging.LogLevel

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object FlowProcessor {

  type IntegrationStep = FlowEnvelope => Try[FlowEnvelope]

  def transform[T](name: String, log: FlowEnvelopeLogger)(f : FlowEnvelope => Try[T])(implicit clazz : ClassTag[T])
    : Graph[FlowShape[FlowEnvelope, Either[FlowEnvelope, T]], NotUsed] = {

    Flow.fromFunction[FlowEnvelope, Either[FlowEnvelope, T]] { env =>
      env.exception match {
        case None =>
          log.logEnv(env, LogLevel.Debug, s"Starting function [${env.id}]:[$name]")
          val start = System.currentTimeMillis()
          f(env) match {
            case Success(s) =>
              log.logEnv(env, LogLevel.Debug, s"Function [${env.id}]:[$name] completed in [${System.currentTimeMillis() - start}]ms")
              Right(s)
            case Failure(t) =>
              log.logEnv(env.withException(t), LogLevel.Warn, s"Failed to create [${clazz.runtimeClass.getName()}] in [${env.id}]:[$name]")
              Left(env.withException(t))
          }
        case Some(_) =>
          log.logEnv(env, LogLevel.Debug, s"Not executing function [${env.id}]:[$name] as envelope has exception [${env.exception.map(_.getMessage()).getOrElse("")}].")
          Left(env)
      }
    }.named(name)
  }

  def fromFunction(name: String, log: FlowEnvelopeLogger)(f: IntegrationStep) : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env : FlowEnvelope =>

      env.exception match {
        case None =>
          log.logEnv(env, LogLevel.Debug, s"Starting Integration step [${env.id}]:[$name]")
          val start = System.currentTimeMillis()

          val calc = f(env)

          val err = calc match {
            case Success(env) => env.exception
            case Failure(t) => Some(t)
          }

          err match {
            case None =>
              log.logEnv(env, LogLevel.Debug, s"Integration step [${env.id}]:[$name] completed in [${System.currentTimeMillis() - start}]ms")
              calc.get

            case Some(t) =>
              log.logEnv(env.withException(t), LogLevel.Debug, s"Exception in FlowProcessor [${env.id}]:[$name] for message [${env.flowMessage}] : [${t.getClass().getSimpleName()} - ${t.getMessage()}]", false)
              env.withException(t)
          }
        case Some(_) =>
          log.logEnv(env, LogLevel.Debug, s"Skipping integration step [${env.id}]:[$name] due to exception caught in flow.")
          env
      }
    }.named(name)
  }

  def log(level : LogLevel.LogLevel, logger : FlowEnvelopeLogger, text : String = "") : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
    logger.logEnv(env, level, s"$text : $env", false)
    env
  }

  def splitEither[L, R]() : Graph[FanOutShape2[Either[L, R], L, R], NotUsed] = {
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val branches = b.add(Broadcast[Either[L, R]](2))
      val isLeft = b.add(Flow[Either[L, R]].collect { case Left(l) => l })
      val isRight = b.add(Flow[Either[L, R]].collect { case Right(r) => r })

      branches ~> isLeft
      branches ~> isRight

      new FanOutShape2(branches.in, isLeft.out, isRight.out)
    }
  }

  def partition[T](p : T => Boolean) : Graph[FanOutShape2[T, T, T], NotUsed] = {

    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val branches = b.add(Broadcast[T](2))
      val isTrue = b.add(Flow[T].filter(p))
      val isFalse = b.add(Flow[T].filterNot(p))

      branches ~> isTrue
      branches ~> isFalse

      new FanOutShape2(branches.in, isTrue.out, isFalse.out)
    }
  }
}

trait FlowProcessor {
  val name : String
  val f : FlowProcessor.IntegrationStep

  def flow(log: FlowEnvelopeLogger) : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] =
    FlowProcessor.fromFunction(
      name, log
    )(f).named(name)
}
