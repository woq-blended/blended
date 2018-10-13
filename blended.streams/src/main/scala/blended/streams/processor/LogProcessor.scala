package blended.streams.processor

import blended.streams.FlowProcessor
import blended.streams.FlowProcessor.IntegrationStep
import blended.util.logging.LogLevel.LogLevel
import blended.util.logging.Logger

import scala.util.Success

case class LogProcessor(
  name : String,
  log: Logger,
  level: LogLevel
) extends FlowProcessor {

  override val f: IntegrationStep = { env =>
    log.log(level, s"$name : ${env.flowMessage}")
    Success(List(env))
  }
}
