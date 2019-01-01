package blended.streams.dispatcher.cbe

object ExtendedDataElements {

  def apply(props : Map[String, String]) : String = {

    props.map{ case (k,v) => ExtendedDataElement(k,v).element }.mkString
  }
}

case class ExtendedDataElement(
  name : String,
  value : String
) {

  val element : String =
    s"""
       |  <extendedDataElements name="$name" type="string">
       |    <values>$value</values>
       |  </extendedDataElements>
    """.stripMargin

}

