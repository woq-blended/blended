package blended.jmx

trait NamingStrategy { 
  val objectName : PartialFunction[Any, JmxObjectName]
}

object NamingStrategyResolver {
  val strategyClassNameProp : String = "strategyClassname"
}

trait NamingStrategyResolver {
  def resolveNamingStrategy(v : Product) : Option[NamingStrategy]
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