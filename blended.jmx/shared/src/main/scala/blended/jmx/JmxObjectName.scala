package blended.jmx

case class JmxObjectName (
  domain : String,
  properties : Map[String,String]
) {

  override val toString: String = {
    val props : List[String] = properties.toList.sorted.map{ case (k,v) => s"$k=$v" }
    s"${getClass().getSimpleName()}($domain:${props.mkString(",")})"
  }
}
