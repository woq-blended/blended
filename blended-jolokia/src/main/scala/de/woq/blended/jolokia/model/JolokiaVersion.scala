package de.woq.blended.jolokia.model

import spray.json._
import DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._

object JolokiaVersion {
  def apply(value: JsValue) = {
    val agent    = value.extract[String]("value" / "protocol")
    val protocol = value.extract[String]("value" / "protocol")
    val config   = value.extract[Map[String,String]]("value" / "config")
    new JolokiaVersion(agent, protocol, config)
  }
}

case class JolokiaVersion(agent: String, protocol: String, config: Map[String,String])
