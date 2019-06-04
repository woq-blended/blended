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
  private[this] def log(level : jul.Level, msg : => String, e : Throwable) : Unit =
    if (underlying.isLoggable(level)) underlying.log(level, msg, e)

  override def error(msg : => String) : Unit = log(jul.Level.SEVERE, msg)
  override def warn(msg : => String) : Unit = log(jul.Level.WARNING, msg)
  override def info(msg : => String) : Unit = log(jul.Level.INFO, msg)
  override def debug(msg : => String) : Unit = log(jul.Level.FINE, msg)
  override def trace(msg : => String) : Unit = log(jul.Level.FINER, msg)

  override def error(e : Throwable)(msg : => String) : Unit = log(jul.Level.SEVERE, msg, e)
  override def warn(e : Throwable)(msg : => String) : Unit = log(jul.Level.WARNING, msg, e)
  override def info(e : Throwable)(msg : => String) : Unit = log(jul.Level.INFO, msg, e)
  override def debug(e : Throwable)(msg : => String) : Unit = log(jul.Level.FINE, msg, e)
  override def trace(e : Throwable)(msg : => String) : Unit = log(jul.Level.FINER, msg, e)
}
