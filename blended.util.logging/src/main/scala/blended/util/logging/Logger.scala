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

object Logger {
  def apply[T: ClassTag]: Logger = {
    val base = scala.reflect.classTag[T].runtimeClass.getName
    try {
      // we expect class loading errors if no slf4j is present
      new LoggerSlf4j(slf4j.LoggerFactory.getLogger(base))
    } catch {
      case _: NoClassDefFoundError | _: ClassNotFoundException =>
        try {
          // fall back to jul
          new LoggerJul(jul.Logger.getLogger(base))
        } catch {
          case _: NoClassDefFoundError | _: ClassNotFoundException =>
            new LoggerNoOp()
        }
    }
  }
}

class LoggerNoOp() extends Logger

    
    
    