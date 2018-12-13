package blended.jolokia

package object protocol {

  trait MBeanSearchDef {
    def jmxDomain : String
    def searchProperties : Map[String, String] = Map.empty

    def pattern: String = searchProperties match {
      case m if m.isEmpty => ""
      case m => m.keys.map( k => s"$k=${m.get(k).get}" ).mkString("", ",", ",")
    }
  }

  trait OperationExecDef {
    def objectName    : String
    def operationName : String
    def parameters    : List[String] = List.empty

    def pattern : String = s"$objectName/$operationName/" + parameters.mkString("/")
  }
}
