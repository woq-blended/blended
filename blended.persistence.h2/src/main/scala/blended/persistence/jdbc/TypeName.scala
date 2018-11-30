package blended.persistence.jdbc

sealed trait TypeName {
  /**
   * Internal storage type name of the type (max 10 characters).
   */
  def name: String
}

object TypeName {
  case object Boolean extends TypeName {
    override def name = "Boolean"
  }
  case object Byte extends TypeName {
    override def name = "Byte"
  }
  case object Short extends TypeName {
    override def name = "Short"
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
  case object Null extends TypeName {
    override def name = "Null"
  }
  case object LongString extends TypeName {
    override def name = "LongString"
  }

  def values: Seq[TypeName] = Seq(String, Long, Int, Short, Byte, Boolean, Double, Float, Array, Object, Null, LongString)

  def fromString(name: String): Option[TypeName] = values.find(_.name == name)

}
