package blended.jms.utils

trait ProviderAware {

  val vendor : String
  val provider : String

  def id : String = s"${getClass().getSimpleName()}($vendor:$provider)"
}
