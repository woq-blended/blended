package blended.streams.message

import blended.streams.FlowHeaderConfig
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.util.logging.LogLevel.LogLevel
import blended.util.logging.Logger

object FlowEnvelopeLogger {

  def mdcPrefix : FlowHeaderConfig => String = headerCfg => headerCfg.prefix + ".env"

  def create(headerCfg : FlowHeaderConfig, log: Logger) : FlowEnvelopeLogger =
    new FlowEnvelopeLogger(log, mdcPrefix(headerCfg))

  def mdcMap(prefix : String, props : FlowMessageProps) : Map[String, String] = props.map { case (k,v) =>
    s"$prefix.$k" -> v.value.toString
  }
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

  private val mdc : FlowEnvelope => Map[String, String] = env => FlowEnvelopeLogger.mdcMap(prefix, env.flowMessage.header)

  def logEnv(env : FlowEnvelope, level : LogLevel, msg : => String) : Unit = logEnv(env, _ => level, _ => msg)

  def logEnv(env : FlowEnvelope, level : FlowEnvelope => LogLevel, msg: FlowEnvelope => String) : Unit = {

    env.exception match {
      case None => underlying.logMdc(mdc(env))(level(env), msg(env))
      case Some(e) => underlying.logMdc(e)(mdc(env))(level(env), msg(env))
    }
  }
}
