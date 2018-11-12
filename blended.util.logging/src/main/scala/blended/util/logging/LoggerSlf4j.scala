package blended.util.logging

import org.slf4j.{ Logger => Slf4jLogger }

class LoggerSlf4j(underlying: Slf4jLogger) extends Logger {

  override def name: String = underlying.getName()

  override def isErrorEnabled: Boolean = underlying.isErrorEnabled()
  override def isWarnEnabled: Boolean = underlying.isWarnEnabled()
  override def isInfoEnabled: Boolean = underlying.isInfoEnabled()
  override def isDebugEnabled: Boolean = underlying.isDebugEnabled()
  override def isTraceEnabled: Boolean = underlying.isTraceEnabled()

  override def error(msg: => String): Unit = if (underlying.isErrorEnabled) underlying.error(msg)
  override def warn(msg: => String): Unit = if (underlying.isWarnEnabled) underlying.warn(msg)
  override def info(msg: => String): Unit = if (underlying.isInfoEnabled) underlying.info(msg)
  override def debug(msg: => String): Unit = if (underlying.isDebugEnabled) underlying.debug(msg)
  override def trace(msg: => String): Unit = if (underlying.isTraceEnabled) underlying.trace(msg)

  override def error(e: Throwable)(msg: => String): Unit = if (underlying.isErrorEnabled) underlying.error(msg, e)
  override def warn(e: Throwable)(msg: => String): Unit = if (underlying.isWarnEnabled) underlying.warn(msg, e)
  override def info(e: Throwable)(msg: => String): Unit = if (underlying.isInfoEnabled) underlying.info(msg, e)
  override def debug(e: Throwable)(msg: => String): Unit = if (underlying.isDebugEnabled) underlying.debug(msg, e)
  override def trace(e: Throwable)(msg: => String): Unit = if (underlying.isTraceEnabled) underlying.trace(msg, e)

}

