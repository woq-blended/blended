package blended.util.logging

import java.util.{logging => jul}

import scala.reflect.ClassTag
import org.slf4j

import scala.collection.mutable

object LogLevel extends Enumeration {

  type LogLevel = Value
  val Error, Warn, Info, Debug, Trace = Value
}

trait Logger extends Serializable {

  import blended.util.logging.LogLevel.LogLevel

  def name : String

  def isErrorEnabled: Boolean = false
  def isWarnEnabled: Boolean = false
  def isInfoEnabled: Boolean = false
  def isDebugEnabled: Boolean = false
  def isTraceEnabled: Boolean = false

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

  def error(e: Throwable)(msg: => String): Unit = {}
  def warn(e: Throwable)(msg: => String): Unit = {}
  def info(e: Throwable)(msg: => String): Unit = {}
  def debug(e: Throwable)(msg: => String): Unit = {}
  def trace(e: Throwable)(msg: => String): Unit = {}

  def log(level: LogLevel, msg: => String) : Unit = level match {
    case LogLevel.Error => error(msg)
    case LogLevel.Warn => warn(msg)
    case LogLevel.Info => info(msg)
    case LogLevel.Debug => debug(msg)
    case LogLevel.Trace => trace(msg)
  }

  def log(t: Throwable)(level: LogLevel, msg: => String) : Unit = level match {
    case LogLevel.Error => error(t)(msg)
    case LogLevel.Warn => warn(t)(msg)
    case LogLevel.Info => info(t)(msg)
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

/**
 * Factory apply-methods try to find a SLF4J-Logger on the classpath
 * and fall-back to use the `java.util.logging` API if no SLF4J logger could be loaded.
 */
object Logger {

  private[this] val mdcPropsMut : mutable.Map[String, String] = mutable.Map.empty[String, String]
  private[this] var mdcMapIntern : Map[String, String] = Map.empty

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
            new LoggerNoOp(name)
        }
    }
  }

  def mdcProps : Map[String, String] = mdcMapIntern

  def setProps(props: Map[String, String]) : Unit = synchronized {
    props.foreach{ case (k,v) => mdcPropsMut.put(k,v) }
    mdcMapIntern = mdcPropsMut.toMap
  }

  def clearMdc() : Unit = synchronized {
    mdcPropsMut.clear()
  }
}

/**
 * A Logger class doing nothing.
 */
class LoggerNoOp(override val name : String) extends Logger
