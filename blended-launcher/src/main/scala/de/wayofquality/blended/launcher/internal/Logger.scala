package de.wayofquality.blended.launcher.internal

import scala.reflect.ClassTag
import scala.reflect.classTag

trait Logger extends Serializable {
  def error(msg: => String, throwable: Throwable = null): Unit
  def warn(msg: => String, throwable: Throwable = null): Unit
  def info(msg: => String, throwable: Throwable = null): Unit
  def debug(msg: => String, throwable: Throwable = null): Unit
  def trace(msg: => String, throwable: Throwable = null): Unit
}

object Logger {

  private[this] var cachedLoggerFactory: Option[String => Logger] = None

  private[this] lazy val noOpLogger = new Logger {
    override def error(msg: => String, throwable: Throwable): Unit = {}
    override def warn(msg: => String, throwable: Throwable): Unit = {}
    override def info(msg: => String, throwable: Throwable): Unit = {}
    override def debug(msg: => String, throwable: Throwable): Unit = {}
    override def trace(msg: => String, throwable: Throwable): Unit = {}
    override def toString = "Noop Logger"
  }
  private[this] lazy val noOpLoggerFactory: String => Logger = _ => noOpLogger

  def apply[T: ClassTag]: Logger = cachedLoggerFactory match {

    case Some(loggerFactory) => loggerFactory(classTag[T].runtimeClass.getName)

    case None =>
      // try to load SLF4J
      def delegatedLoadingOfLoggerFactory: String => Logger = {
        // trigger loading of class, risking a NoClassDefFounError
        org.slf4j.LoggerFactory.getILoggerFactory()

        // if we are here, loading the LoggerFactory was successful
        loggedClass => new Logger {
          private[this] val underlying = org.slf4j.LoggerFactory.getLogger(loggedClass)
          override def error(msg: => String, throwable: Throwable) = if (underlying.isErrorEnabled) underlying.error(msg, throwable)
          override def warn(msg: => String, throwable: Throwable) = if (underlying.isWarnEnabled) underlying.warn(msg, throwable)
          override def info(msg: => String, throwable: Throwable) = if (underlying.isInfoEnabled) underlying.info(msg, throwable)
          override def debug(msg: => String, throwable: Throwable) = if (underlying.isDebugEnabled) underlying.debug(msg, throwable)
          override def trace(msg: => String, throwable: Throwable) = if (underlying.isTraceEnabled) underlying.trace(msg, throwable)
          override def toString = "Slf4j bridge wrapper for: " + loggedClass
        }
      }

      try {
        val loggerFactory = delegatedLoadingOfLoggerFactory
        cachedLoggerFactory = Some(loggerFactory)
        loggerFactory(classTag[T].runtimeClass.getName)
      } catch {
        case e: NoClassDefFoundError =>
          cachedLoggerFactory = Some(noOpLoggerFactory)
          noOpLogger
      }
  }

}

