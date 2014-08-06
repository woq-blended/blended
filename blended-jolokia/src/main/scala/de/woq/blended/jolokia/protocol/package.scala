package de.woq.blended.jolokia

package object protocol {

  trait MBeanSearchSpec {
    def jmxDomain : String
    def searchProperties : Map[String, String] = Map.empty

    def pattern = searchProperties match {
      case m if m.isEmpty => ""
      case m => m.keys.map( k => s"${k}=${m.get(k).get}" ).mkString(",")
    }
  }

  case object GetJolokiaVersion
  case class  SearchJolokia(searchSpec : MBeanSearchSpec)
  case class  ReadJolokiaMBean(objectName: String)
}
