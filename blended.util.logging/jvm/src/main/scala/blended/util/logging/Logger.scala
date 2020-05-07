package blended.util.logging

import java.util.{logging => jul}

import org.slf4j

import scala.collection.mutable
import scala.reflect.ClassTag

trait Logger extends LoggerBase

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
  def apply[T : ClassTag] : Logger = {
    val name = scala.reflect.classTag[T].runtimeClass.getName
    apply(name)
  }

  /**
   * Create a Logger instance with the given name.
   */
  def apply(name : String) : Logger = {
    try {
      // we expect class loading errors if no slf4j is present
      new LoggerSlf4j(slf4j.LoggerFactory.getLogger(name))
    } catch {
      case _ : NoClassDefFoundError | _ : ClassNotFoundException =>
        try {
          // fall back to jul
          new LoggerJul(jul.Logger.getLogger(name))
        } catch {
          case _ : NoClassDefFoundError | _ : ClassNotFoundException =>
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
