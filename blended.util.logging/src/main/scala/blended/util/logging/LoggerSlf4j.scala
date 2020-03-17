package blended.util.logging

import org.slf4j.{MDC, Logger => Slf4jLogger}

class LoggerSlf4j(underlying : Slf4jLogger) extends Logger {

  import Logger.mdcProps

  override def name: String = underlying.getName()

  private def withMDC(mdc : Map[String, String])(logStatement: => Unit) : Unit = {

    mdcProps.foreach{ case (k,v) => MDC.put(k,v) }
    mdc.foreach{ case (k,v) => MDC.put(k,v) }

    try {
      logStatement
    } finally {
      mdc.keys.foreach( MDC.remove )
      mdcProps.keys.foreach( MDC.remove )
    }
  }

  override def isErrorEnabled: Boolean = underlying.isErrorEnabled()
  override def isWarnEnabled: Boolean = underlying.isWarnEnabled()
  override def isInfoEnabled: Boolean = underlying.isInfoEnabled()
  override def isDebugEnabled: Boolean = underlying.isDebugEnabled()
  override def isTraceEnabled: Boolean = underlying.isTraceEnabled()

  override def errorMdc(mdc : Map[String, String])(msg: => String) : Unit = if (underlying.isErrorEnabled) withMDC (mdc) { underlying.error(msg) }
  override def warnMdc(mdc : Map[String, String])(msg: => String) : Unit = if (underlying.isWarnEnabled) withMDC (mdc) { underlying.warn(msg) }
  override def infoMdc(mdc : Map[String, String])(msg: => String) : Unit = if (underlying.isInfoEnabled) withMDC (mdc) { underlying.info(msg) }
  override def debugMdc(mdc : Map[String, String])(msg: => String) : Unit = if (underlying.isDebugEnabled) withMDC (mdc) { underlying.debug(msg) }
  override def traceMdc(mdc : Map[String, String])(msg: => String) : Unit = if (underlying.isTraceEnabled) withMDC (mdc) { underlying.trace(msg) }

  override def errorMdc(e : Throwable)(mdc : Map[String, String])(msg: => String) : Unit =
    if (underlying.isErrorEnabled) withMDC (mdc) { underlying.error(msg, e) }
  override def warnMdc(e : Throwable)(mdc : Map[String, String])(msg: => String) : Unit =
    if (underlying.isWarnEnabled) withMDC (mdc) { underlying.warn(msg, e) }
  override def infoMdc(e : Throwable)(mdc : Map[String, String])(msg: => String) : Unit =
    if (underlying.isInfoEnabled) withMDC (mdc) { underlying.info(msg, e) }
  override def debugMdc(e : Throwable)(mdc : Map[String, String])(msg: => String) : Unit =
    if (underlying.isDebugEnabled) withMDC (mdc) { underlying.debug(msg, e) }
  override def traceMdc(e : Throwable)(mdc : Map[String, String])(msg: => String) : Unit =
    if (underlying.isTraceEnabled) withMDC (mdc) { underlying.trace(msg, e) }

  override def error(msg: => String): Unit = if (underlying.isErrorEnabled) errorMdc(Map.empty[String, String])(msg)
  override def warn(msg: => String): Unit = if (underlying.isWarnEnabled) warnMdc(Map.empty[String, String])(msg)
  override def info(msg: => String): Unit = if (underlying.isInfoEnabled) infoMdc(Map.empty[String, String])(msg)
  override def debug(msg: => String): Unit = if (underlying.isDebugEnabled) debugMdc(Map.empty[String, String])(msg)
  override def trace(msg: => String): Unit = if (underlying.isTraceEnabled) traceMdc(Map.empty[String, String])(msg)

  override def error(e: Throwable)(msg: => String): Unit = if (underlying.isErrorEnabled) errorMdc(e)(Map.empty[String, String])(msg)
  override def warn(e: Throwable)(msg: => String): Unit = if (underlying.isWarnEnabled) warnMdc(e)(Map.empty[String, String])(msg)
  override def info(e: Throwable)(msg: => String): Unit = if (underlying.isInfoEnabled) infoMdc(e)(Map.empty[String, String])(msg)
  override def debug(e: Throwable)(msg: => String): Unit = if (underlying.isDebugEnabled) debugMdc(e)(Map.empty[String, String])(msg)
  override def trace(e: Throwable)(msg: => String): Unit = if (underlying.isTraceEnabled) traceMdc(e)(Map.empty[String, String])(msg)

}

