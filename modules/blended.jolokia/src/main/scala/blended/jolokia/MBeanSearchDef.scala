package blended.jolokia

case class MBeanSearchDef(
  jmxDomain : String,
  searchProperties : Map[String, String] = Map.empty
) {
  def pattern : String = searchProperties match {
    case m if m.isEmpty => ""
    case m              => m.keys.map(k => s"$k=${m.get(k).get}").mkString("", ",", ",")
  }
}

case class OperationExecDef(
  objectName : String,
  operationName : String,
  parameters : List[String] = List.empty
) {
  def pattern : String = s"$objectName/$operationName/" + parameters.mkString("/")
}
