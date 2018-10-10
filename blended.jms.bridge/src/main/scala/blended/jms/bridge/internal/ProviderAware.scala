package blended.jms.bridge.internal

trait ProviderAware {

  val vendor : String
  val provider : Option[String]
}
