package blended.util.logging

import java.util.{ logging => jul }

import scala.reflect.ClassTag

import org.slf4j

trait Logger extends Serializable {

  def isErrorEnabled: Boolean = false
  def isWarnEnabled: Boolean = false
  def isInfoEnabled: Boolean = false
  def isDebugEnabled: Boolean = false
  def isTraceEnabled: Boolean = false

  def error(msg: => String): Unit = {}
  def warn(msg: => String): Unit = {}
  def info(msg: => String): Unit = {}
  def debug(msg: => String): Unit = {}
  def trace(msg: => String): Unit = {}

  def error(e: Throwable)(msg: => String): Unit = {}
  def warn(e: Throwable)(msg: => String): Unit = {}
  def info(e: Throwable)(msg: => String): Unit = {}
  def debug(e: Throwable)(msg: => String): Unit = {}
  def trace(e: Throwable)(msg: => String): Unit = {}

}

/**
 * Factory apply-methods try to find a SLF4J-Logger on the classpath
 * and fall-back to use the `java.util.logging` API if no SLF4J logger could be loaded.
 */
object Logger {

  /**
   * Create a Logger instance by deriving the logger name from the fully qualified class name.
   */
  def apply[T: ClassTag]: Logger = {
    val name = scala.reflect.classTag[T].runtimeClass.getName
    apply(name)
  }

  /**
   * Create a Logger instance with the given name.
   */
  def apply(name: String): Logger = {
    try {
      // we expect class loading errors if no slf4j is present
      new LoggerSlf4j(slf4j.LoggerFactory.getLogger(name))
    } catch {
      case _: NoClassDefFoundError | _: ClassNotFoundException =>
        try {
          // fall back to jul
          new LoggerJul(jul.Logger.getLogger(name))
        } catch {
          case _: NoClassDefFoundError | _: ClassNotFoundException =>
            new LoggerNoOp()
        }
    }
  }
}

/**
 * A Logger class doing nothing.
 */
class LoggerNoOp() extends Logger
