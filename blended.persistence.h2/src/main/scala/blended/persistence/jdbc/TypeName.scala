package blended.persistence.jdbc

sealed trait TypeName {
  /**
   * Internal storage type name of the type (max 10 characters).
   */
  def name : String
}

object TypeName {
  case object Boolean extends TypeName {
    override def name : String = "Boolean"
  }
  case object Byte extends TypeName {
    override def name : String = "Byte"
  }
  case object Short extends TypeName {
    override def name : String = "Short"
  }
  case object Int extends TypeName {
    override def name : String = "Int"
  }
  case object Long extends TypeName {
    override def name : String = "Long"
  }
  case object Float extends TypeName {
    override def name : String = "Float"
  }
  case object Double extends TypeName {
    override def name : String = "Double"
  }
  case object String extends TypeName {
    override def name : String = "String"
  }
  case object Array extends TypeName {
    override def name : String = "Array"
  }
  case object Object extends TypeName {
    override def name : String = "Object"
  }
  case object Null extends TypeName {
    override def name : String = "Null"
  }
  case object LongString extends TypeName {
    override def name : String = "LongString"
  }

  def values : Seq[TypeName] = Seq(String, Long, Int, Short, Byte, Boolean, Double, Float, Array, Object, Null, LongString)

  def fromString(name : String) : Option[TypeName] = values.find(_.name == name)

}
