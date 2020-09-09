package blended.util.logging

import java.util.{logging => jul}

class LoggerJul(underlying : jul.Logger) extends Logger {

  override def name : String = underlying.getName()

  override def isErrorEnabled : Boolean = underlying.isLoggable(jul.Level.SEVERE)
  override def isWarnEnabled : Boolean = underlying.isLoggable(jul.Level.WARNING)
  override def isInfoEnabled : Boolean = underlying.isLoggable(jul.Level.INFO)
  override def isDebugEnabled : Boolean = underlying.isLoggable(jul.Level.FINE)
  override def isTraceEnabled : Boolean = underlying.isLoggable(jul.Level.FINER)

  @inline
  private[this] def log(level : jul.Level, msg : => String) : Unit =
    if (underlying.isLoggable(level)) underlying.log(level, msg)

  @inline
  private[this] def log(level : jul.Level, msg : => String, stacktrace : Boolean, e : Throwable) : Unit =
    if (underlying.isLoggable(level)) underlying.log(level, includeStacktrace(e, stacktrace)(msg), e)

  override def error(msg : => String) : Unit = log(jul.Level.SEVERE, msg)
  override def warn(msg : => String) : Unit = log(jul.Level.WARNING, msg)
  override def info(msg : => String) : Unit = log(jul.Level.INFO, msg)
  override def debug(msg : => String) : Unit = log(jul.Level.FINE, msg)
  override def trace(msg : => String) : Unit = log(jul.Level.FINER, msg)

  override def error(e : Throwable, stacktrace : Boolean)(msg : => String) : Unit =
    log(jul.Level.SEVERE, msg, stacktrace, e)

  override def warn(e : Throwable, stacktrace : Boolean = false)(msg : => String) : Unit =
    log(jul.Level.WARNING, msg, stacktrace, e)

  override def info(e : Throwable, stacktrace : Boolean = false)(msg : => String) : Unit =
    log(jul.Level.INFO, msg, stacktrace, e)

  override def debug(e : Throwable, stacktrace : Boolean = false)(msg : => String) : Unit =
    log(jul.Level.FINE, msg, stacktrace, e)

  override def trace(e : Throwable, stacktrace : Boolean = false)(msg : => String) : Unit =
    log(jul.Level.FINER, msg, stacktrace, e)
}
