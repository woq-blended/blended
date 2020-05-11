package blended.util.logging

trait LoggerBase extends Serializable {

  import blended.util.logging.LogLevel.LogLevel

  def name : String

  def isErrorEnabled : Boolean = false
  def isWarnEnabled : Boolean = false
  def isInfoEnabled : Boolean = false
  def isDebugEnabled : Boolean = false
  def isTraceEnabled : Boolean = false

  def errorMdc(mdc : Map[String, String])(msg: => String) : Unit = error(msg)
  def warnMdc(mdc : Map[String, String])(msg: => String) : Unit = warn(msg)
  def infoMdc(mdc : Map[String, String])(msg: => String) : Unit = info(msg)
  def debugMdc(mdc : Map[String, String])(msg: => String) : Unit = debug(msg)
  def traceMdc(mdc : Map[String, String])(msg: => String) : Unit = trace(msg)

  def errorMdc(e: Throwable)(mdc : Map[String, String])(msg: => String) : Unit = error(e)(msg)
  def warnMdc(e: Throwable)(mdc : Map[String, String])(msg: => String) : Unit = warn(e)(msg)
  def infoMdc(e: Throwable)(mdc : Map[String, String])(msg: => String) : Unit = info(e)(msg)
  def debugMdc(e: Throwable)(mdc : Map[String, String])(msg: => String) : Unit = debug(e)(msg)
  def traceMdc(e: Throwable)(mdc : Map[String, String])(msg: => String) : Unit = trace(e)(msg)

  def error(msg: => String): Unit = {}
  def warn(msg: => String): Unit = {}
  def info(msg: => String): Unit = {}
  def debug(msg: => String): Unit = {}
  def trace(msg: => String): Unit = {}

  def error(e : Throwable)(msg : => String) : Unit = {}
  def warn(e : Throwable)(msg : => String) : Unit = {}
  def info(e : Throwable)(msg : => String) : Unit = {}
  def debug(e : Throwable)(msg : => String) : Unit = {}
  def trace(e : Throwable)(msg : => String) : Unit = {}

  def log(level : LogLevel, msg : => String) : Unit = level match {
    case LogLevel.Error => error(msg)
    case LogLevel.Warn  => warn(msg)
    case LogLevel.Info  => info(msg)
    case LogLevel.Debug => debug(msg)
    case LogLevel.Trace => trace(msg)
  }

  def log(t : Throwable)(level : LogLevel, msg : => String) : Unit = level match {
    case LogLevel.Error => error(t)(msg)
    case LogLevel.Warn  => warn(t)(msg)
    case LogLevel.Info  => info(t)(msg)
    case LogLevel.Debug => debug(t)(msg)
    case LogLevel.Trace => trace(t)(msg)
  }

  def logMdc(mdc : Map[String, String])(level : LogLevel, msg: => String) : Unit = level match {
    case LogLevel.Error => errorMdc(mdc)(msg)
    case LogLevel.Warn => warnMdc(mdc)(msg)
    case LogLevel.Info => infoMdc(mdc)(msg)
    case LogLevel.Debug => debugMdc(mdc)(msg)
    case LogLevel.Trace => traceMdc(mdc)(msg)
  }

  def logMdc(e: Throwable)(mdc : Map[String, String])(level : LogLevel, msg: => String) : Unit = level match {
    case LogLevel.Error => errorMdc(e)(mdc)(msg)
    case LogLevel.Warn => warnMdc(e)(mdc)(msg)
    case LogLevel.Info => infoMdc(e)(mdc)(msg)
    case LogLevel.Debug => debugMdc(e)(mdc)(msg)
    case LogLevel.Trace => traceMdc(e)(mdc)(msg)
  }
}
