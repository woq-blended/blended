package blended.akka.logging

import akka.actor.{Actor, ActorSystem}
import akka.dispatch.RequiresMessageQueue
import akka.event.{DummyClassForStringSources, LoggerMessageQueueSemantics}
import akka.event._
import akka.event.Logging._
import akka.util.Helpers
import blended.util.logging.Logger

/**
 * Base trait for all classes that wants to be able use the SLF4J logging infrastructure.
 */
trait BlendedLogging {
  @transient
  lazy val log : Logger = Logger(this.getClass.getName)
}

/**
 * Logger is a factory for obtaining SLF4J-Loggers
 */
object AkkaLogger {
  /**
   * @param logger - which logger
   * @return a Logger that corresponds for the given logger name
   */
  def apply(logger: String): Logger = Logger(logger)

  /**
   * @param logClass - the class to log for
   * @param logSource - the textual representation of the source of this log stream
   * @return a Logger for the specified parameters
   */
  def apply(logClass: Class[_], logSource: String): Logger = logClass match {
    case c if c == classOf[DummyClassForStringSources] => apply(logSource)
    case _ => apply(getClass().getName())
  }
}

/**
 * Blended logger.
 *
 * The thread in which the logging was performed is captured in
 * Mapped Diagnostic Context (MDC) with attribute name "sourceThread".
 */
class BlendedLogger extends Actor with BlendedLogging with RequiresMessageQueue[LoggerMessageQueueSemantics] {

  val mdcThreadAttributeName = "sourceThread"
  val mdcActorSystemAttributeName = "sourceActorSystem"
  val mdcAkkaSourceAttributeName = "akkaSource"
  val mdcAkkaTimestamp = "akkaTimestamp"

  def receive : Receive = {

    case event @ Error(cause, logSource, logClass, message) =>
      cause match {
        case Error.NoCause | null =>
          AkkaLogger(logClass, logSource).errorMdc(mdcMap(logSource, event))(Option(message).map(_.toString).orNull)
        case _ =>
          AkkaLogger(logClass, logSource).errorMdc(cause)(mdcMap(logSource, event))(Option(message).map(_.toString).getOrElse(cause.getLocalizedMessage))
      }

    case event @ Warning(logSource, logClass, message) ⇒
      event match {
        case e: LogEventWithCause ⇒
          AkkaLogger(logClass, logSource).warnMdc(e.cause)(mdcMap(logSource, event))(Option(message).map(_.toString).getOrElse(e.cause.getLocalizedMessage))
        case _                    ⇒
          AkkaLogger(logClass, logSource).warnMdc(mdcMap(logSource, event))(Option(message).map(_.toString).orNull)
      }

    case event @ Info(logSource, logClass, message) =>
      AkkaLogger(logClass, logSource).infoMdc(mdcMap(logSource, event))(message.toString)

    case event @ Debug(logSource, logClass, message) ⇒
      AkkaLogger(logClass, logSource).debugMdc(mdcMap(logSource, event))(message.toString)

    case InitializeLogger(_) =>
      log.info("Blended Akka Logger started")
      sender() ! LoggerInitialized
  }

  @inline
  final def mdcMap(logSource: String, logEvent: LogEvent) : Map[String, String] = Map(
    mdcAkkaSourceAttributeName -> logSource,
    mdcThreadAttributeName -> logEvent.thread.getName(),
    mdcAkkaTimestamp -> Helpers.currentTimeMillisToUTCString(logEvent.timestamp),
    mdcActorSystemAttributeName -> context.system.name
  )
}

/**
 * [[akka.event.LoggingFilter]] that uses the log level defined in the SLF4J
 * backend configuration (e.g. logback.xml) to filter log events before publishing
 * the log events to the `eventStream`.
 */
class BlendedLoggingFilter(settings: ActorSystem.Settings, eventStream: EventStream) extends LoggingFilter {
  def isErrorEnabled(logClass: Class[_], logSource: String) : Boolean =
    (eventStream.logLevel >= ErrorLevel) && AkkaLogger(logClass, logSource).isErrorEnabled
  def isWarningEnabled(logClass: Class[_], logSource: String) : Boolean =
    (eventStream.logLevel >= WarningLevel) && AkkaLogger(logClass, logSource).isWarnEnabled
  def isInfoEnabled(logClass: Class[_], logSource: String) : Boolean =
    (eventStream.logLevel >= InfoLevel) && AkkaLogger(logClass, logSource).isInfoEnabled
  def isDebugEnabled(logClass: Class[_], logSource: String) : Boolean =
    (eventStream.logLevel >= DebugLevel) && AkkaLogger(logClass, logSource).isDebugEnabled
}
