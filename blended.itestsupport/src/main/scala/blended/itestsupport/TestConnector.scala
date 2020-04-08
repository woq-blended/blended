package blended.itestsupport

import java.util.concurrent.ConcurrentHashMap

import blended.util.logging.Logger

import scala.reflect.ClassTag
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object TestConnector {

  private val log : Logger = Logger[TestConnector.type]

  private val connectProperties : ConcurrentHashMap[String, Any] = new ConcurrentHashMap[String, Any]()

  def put(key : String, value : Any) : Unit = connectProperties.put(key, value)

  def property[T](key : String)(implicit clazz : ClassTag[T]) : Try[T] = {
    Option(connectProperties.get(key)) match {
      // TODO: Is the condition correct or should it be t.getClass.isAssignableFrom ... ??
      case Some(t) if clazz.runtimeClass.isAssignableFrom(t.getClass) =>
        Success(t.asInstanceOf[T])
      case _ =>
        val msg = s"Failed to look up property [$key] from TestConnector"
        log.debug(msg)
        Failure(new Exception(msg))
    }
  }

  def properties : Map[String, Any] = connectProperties.asScala.toMap
}

/**
  * Used to set up the test connector.
  */
trait TestConnectorSetup {
  def configure(cuts: Map[String, ContainerUnderTest]): Unit
}
