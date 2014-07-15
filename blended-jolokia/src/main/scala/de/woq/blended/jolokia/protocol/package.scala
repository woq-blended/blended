package de.woq.blended.jolokia

package object protocol {

  case object GetJolokiaVersion
  case class  SearchJolokia(pattern: String)
  case class  ReadJolokiaMBean(objectName: String)
}
