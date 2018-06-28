package blended.persistence.jdbc

sealed trait TypeName {
  def name: String
}

object TypeName {
  case object Boolean extends TypeName {
    override def name = "Boolean"
  }
  case object Byte extends TypeName {
    override def name = "Byte"
  }
  case object Int extends TypeName {
    override def name = "Int"
  }
  case object Long extends TypeName {
    override def name = "Long"
  }
  case object Float extends TypeName {
    override def name = "Float"
  }
  case object Double extends TypeName {
    override def name = "Double"
  }
  case object String extends TypeName {
    override def name = "String"
  }
  case object Array extends TypeName {
    override def name = "Array"
  }
  case object Object extends TypeName {
    override def name = "Object"
  }

  def fromString(name: String): Option[TypeName] =
    Seq(String, Long, Int, Byte, Boolean, Double, Float, Array, Object).find(_.name == name)

}
