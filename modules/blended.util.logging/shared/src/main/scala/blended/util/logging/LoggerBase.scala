package blended.util.logging

import java.io.{PrintWriter, StringWriter}

import blended.util.logging.LogLevel.LogLevel

trait LoggerBase extends Serializable {

  def name : String

  def isErrorEnabled : Boolean = false
  def isWarnEnabled : Boolean = false
  def isInfoEnabled : Boolean = false
  def isDebugEnabled : Boolean = false
  def isTraceEnabled : Boolean = false

  def error(msg: => String): Unit = {}
  def warn(msg: => String): Unit = {}
  def info(msg: => String): Unit = {}
  def debug(msg: => String): Unit = {}
  def trace(msg: => String): Unit = {}

  protected def includeStacktrace(e : Throwable, stacktrace: Boolean)(msg : => String) : String = {
    if (stacktrace) {
      val sw : StringWriter = new StringWriter()
      val pw : PrintWriter = new PrintWriter(sw)
      e.printStackTrace(pw)
      pw.close()
      sw.close()
      sw.toString() + "\n" + msg
    } else {
      msg
    }
  }

  def error(e : Throwable, stackTrace : Boolean = false)(msg : => String) : Unit = {}
  def warn(e : Throwable, stackTrace : Boolean = false)(msg : => String) : Unit = {}
  def info(e : Throwable, stackTrace : Boolean = false)(msg : => String) : Unit = {}
  def debug(e : Throwable, stackTrace : Boolean = false)(msg : => String) : Unit = {}
  def trace(e : Throwable, stackTrace: Boolean = false)(msg : => String) : Unit = {}

  def log(level : LogLevel, msg : => String) : Unit = level match {
    case LogLevel.Error => error(msg)
    case LogLevel.Warn  => warn(msg)
    case LogLevel.Info  => info(msg)
    case LogLevel.Debug => debug(msg)
    case LogLevel.Trace => trace(msg)
  }

  def log(t : Throwable, stacktrace : Boolean = false)(level : LogLevel, msg : => String) : Unit = level match {
    case LogLevel.Error => error(t, stacktrace)(msg)
    case LogLevel.Warn  => warn(t, stacktrace)(msg)
    case LogLevel.Info  => info(t, stacktrace)(msg)
    case LogLevel.Debug => debug(t, stacktrace)(msg)
    case LogLevel.Trace => trace(t, stacktrace)(msg)
  }

}

trait LoggerMdcBase extends LoggerBase {

  def errorMdc(mdc : Map[String, String])(msg: => String) : Unit = error(msg)
  def warnMdc(mdc : Map[String, String])(msg: => String) : Unit = warn(msg)
  def infoMdc(mdc : Map[String, String])(msg: => String) : Unit = info(msg)
  def debugMdc(mdc : Map[String, String])(msg: => String) : Unit = debug(msg)
  def traceMdc(mdc : Map[String, String])(msg: => String) : Unit = trace(msg)

  def errorMdc(e: Throwable, stacktrace : Boolean = false)(mdc : Map[String, String])(msg: => String) : Unit = error(e, stacktrace)(msg)
  def warnMdc(e: Throwable, stacktrace : Boolean = false)(mdc : Map[String, String])(msg: => String) : Unit = warn(e, stacktrace)(msg)
  def infoMdc(e: Throwable, stacktrace : Boolean = false)(mdc : Map[String, String])(msg: => String) : Unit = info(e, stacktrace)(msg)
  def debugMdc(e: Throwable, stacktrace : Boolean = false)(mdc : Map[String, String])(msg: => String) : Unit = debug(e, stacktrace)(msg)
  def traceMdc(e: Throwable, stacktrace : Boolean = false)(mdc : Map[String, String])(msg: => String) : Unit = trace(e, stacktrace)(msg)

  def logMdc(mdc : Map[String, String])(level : LogLevel, msg: => String) : Unit = level match {
    case LogLevel.Error => errorMdc(mdc)(msg)
    case LogLevel.Warn => warnMdc(mdc)(msg)
    case LogLevel.Info => infoMdc(mdc)(msg)
    case LogLevel.Debug => debugMdc(mdc)(msg)
    case LogLevel.Trace => traceMdc(mdc)(msg)
  }

  def logMdc(e: Throwable, stacktrace : Boolean = false)(mdc : Map[String, String])(level : LogLevel, msg: => String) : Unit = level match {
    case LogLevel.Error => errorMdc(e, stacktrace)(mdc)(msg)
    case LogLevel.Warn => warnMdc(e, stacktrace)(mdc)(msg)
    case LogLevel.Info => infoMdc(e, stacktrace)(mdc)(msg)
    case LogLevel.Debug => debugMdc(e, stacktrace)(mdc)(msg)
    case LogLevel.Trace => traceMdc(e, stacktrace)(mdc)(msg)
  }
}
