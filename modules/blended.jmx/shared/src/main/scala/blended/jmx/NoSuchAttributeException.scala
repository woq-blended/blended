package blended.jmx

class NoSuchAttributeException(objectName: JmxObjectName, attr: String) extends
  Exception(s"Attribute [$attr] does not exist in [${objectName.objectName}]")

class InvalidAttributeTypeException(objectName: JmxObjectName, attr : String, expType : Class[_]) extends
  Exception(s"Invalid attribute type in [${objectName.objectName}], attribute [$attr], extpected [${expType.getName()}")
