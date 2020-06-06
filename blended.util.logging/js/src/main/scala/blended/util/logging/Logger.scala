package blended.util.logging

import java.util.{logging => jul}

import scala.collection.mutable
import scala.reflect.ClassTag

trait Logger extends LoggerBase

class LoggerJs(override val name: String) extends Logger {

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
  def apply[T : ClassTag] : Logger = {
    val name = scala.reflect.classTag[T].runtimeClass.getName
    apply(name)
  }

  /**
   * Create a Logger instance with the given name.
   */
  def apply(name : String) : Logger = {
    new LoggerJs(name)
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
