package blended.jolokia

import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.json.lenses.JsonLenses._

sealed trait JolokiaObject

object JolokiaExecResult {
  def apply(result : JsValue) : JolokiaExecResult = {
    val objectName = result.extract[String]("request" / "mbean")
    val operation = result.extract[String]("request" / "operation")
    val value = result.extract[JsValue]("value")
    new JolokiaExecResult(objectName, operation, value)
  }
}

case class JolokiaExecResult(
  objectName : String,
  operationName : String,
  value : JsValue
) extends JolokiaObject

object JolokiaReadResult {
  def apply(objectName : String, jsValue : JsValue) : JolokiaReadResult = {
    new JolokiaReadResult(
      objectName = objectName,
      jsValue.extract[Map[String, JsValue]]("value")
    )
  }
}

case class JolokiaReadResult(
  objectName : String,
  attributes : Map[String, JsValue]
) extends JolokiaObject

object JolokiaSearchResult {
  def apply(jsValue : JsValue) : JolokiaSearchResult = {
    val mbeanNames = jsValue.extract[List[String]]("value")
    new JolokiaSearchResult(mbeanNames)
  }
}

case class JolokiaSearchResult(mbeanNames : List[String]) extends JolokiaObject

object JolokiaVersion {
  def apply(value : JsValue) : JolokiaVersion = {
    val agent = value.extract[String]("value" / "agent")
    val protocol = value.extract[String]("value" / "protocol")
    val config = value.extract[Map[String, String]]("value" / "config")
    new JolokiaVersion(agent, protocol, config)
  }
}

case class JolokiaVersion(
  agent : String,
  protocol : String,
  config : Map[String, String]
) extends JolokiaObject
