package blended.jolokia.model

import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.json.lenses.JsonLenses._

object JolokiaReadResult {
  def apply(jsValue: JsValue): JolokiaReadResult = {
    new JolokiaReadResult(
      jsValue.extract[String]("value" / "ObjectName" / "objectName"),
      jsValue.extract[Map[String, JsValue]]("value")
    )
  }
}

case class JolokiaReadResult(objectName: String, attributes: Map[String, JsValue])
