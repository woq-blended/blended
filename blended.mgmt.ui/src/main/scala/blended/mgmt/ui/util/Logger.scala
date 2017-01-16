package blended.mgmt.ui.util

import scala.reflect.ClassTag
import scala.reflect.classTag

trait Logger extends Serializable {
  def error(msg: => String, throwable: Throwable = null)
  def warn(msg: => String, throwable: Throwable = null)
  def info(msg: => String, throwable: Throwable = null)
  def debug(msg: => String, throwable: Throwable = null)
  def trace(msg: => String, throwable: Throwable = null)
}

class PrintlnLogger(className: String) extends Logger {
  override def error(msg: => String, throwable: Throwable) = log("ERROR", msg, throwable)
  override def warn(msg: => String, throwable: Throwable) = log("WARN", msg, throwable)
  override def info(msg: => String, throwable: Throwable) = log("INFO", msg, throwable)
  override def debug(msg: => String, throwable: Throwable) = log("DEBUG", msg, throwable)
  override def trace(msg: => String, throwable: Throwable) = log("TRACE", msg, throwable)
  def log(level: String, msg: => String, throwable: Throwable) = {
    println(level + " " + className + ": " + msg)
    if (throwable != null) {
      println(throwable)
    }
  }
  override def toString(): String = getClass().getSimpleName() + "(className=" + className + ")"
}

object Logger {

  private[this] lazy val noOpLogger = new Logger {
    override def error(msg: => String, throwable: Throwable) {}
    override def warn(msg: => String, throwable: Throwable) {}
    override def info(msg: => String, throwable: Throwable) {}
    override def debug(msg: => String, throwable: Throwable) {}
    override def trace(msg: => String, throwable: Throwable) {}
  }

  def apply[T: ClassTag]: Logger = {
    apply(classTag[T].runtimeClass.getName)
  }

  def apply(className: String): Logger = {
    // for now, delegate all log message to println
    new PrintlnLogger(className)
  }

}

