package blended.jmx

case class JmxObjectName(
  domain : String,
  properties : Map[String,String]
)
