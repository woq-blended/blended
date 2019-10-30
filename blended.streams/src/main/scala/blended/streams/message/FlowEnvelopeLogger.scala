package blended.streams.message

import blended.streams.FlowHeaderConfig
import blended.util.logging.LogLevel.LogLevel
import blended.util.logging.Logger

object FlowEnvelopeLogger {

  def create(headerCfg : FlowHeaderConfig, log: Logger) : FlowEnvelopeLogger =
    new FlowEnvelopeLogger(log, headerCfg.prefix + ".env")
}

/**
 * A helper class to produce logging entries when a flow envelope is part of the logging information.
 */
class FlowEnvelopeLogger(
  // The underlying blended logger that will perform the actual logging
  val underlying : Logger,
  // A prefix to qualify the message properties within the MDC
  prefix : String
) {

  private val mdc : FlowEnvelope => Map[String, String] = env => env.flowMessage.header.map { case (k,v) =>
    s"$prefix.$k" -> v.value.toString
  }

  def logEnv(env : FlowEnvelope, level : LogLevel, msg : => String) : Unit = logEnv(env, _ => level, _ => msg)

  def logEnv(env : FlowEnvelope, level : FlowEnvelope => LogLevel, msg: FlowEnvelope => String) : Unit = {

    env.exception match {
      case None => underlying.logMdc(mdc(env))(level(env), msg(env))
      case Some(e) => underlying.logMdc(e)(mdc(env))(level(env), msg(env))
    }
  }
}
