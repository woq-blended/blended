package de.woq.blended.jolokia.model

import spray.json._
import DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._

object JolokiaSearchResult {
  def apply(jsValue: JsValue) = {
    val mbeanNames = jsValue.extract[List[String]]("value")
    new JolokiaSearchResult(mbeanNames)
  }
}
case class JolokiaSearchResult(mbeanNames: List[String])
