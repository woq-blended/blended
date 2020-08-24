package blended.jmx

trait NamingStrategy { 
  val objectName : PartialFunction[Any, JmxObjectName]
}

class DefaultNamingStrategy extends NamingStrategy {

  override val objectName : PartialFunction[Any, JmxObjectName] = {
    case v : Product => 
      val c = v.getClass()
      new JmxObjectName(
        domain = c.getPackage().getName(),
        properties = Map("type" -> c.getClass().getSimpleName())
      )
  }
}