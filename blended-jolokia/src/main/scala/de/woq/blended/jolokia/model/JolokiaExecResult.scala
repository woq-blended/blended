package de.woq.blended.jolokia.model

import spray.json._
import spray.json.lenses.JsonLenses._
import DefaultJsonProtocol._

object JolokiaExecResult {
  def apply(result : JsValue) = {
    val objectName = result.extract[String]("request" / "mbean")
    val operation  = result.extract[String]("request" / "operation")
    val value = result.extract[JsValue]("value")
    new JolokiaExecResult(objectName, operation, value)
  }
}

case class JolokiaExecResult(
  objectName : String,
  operationName : String,
  value: JsValue
)
