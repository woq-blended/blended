package blended.updater

trait ProfileActivator {
  def apply(name : String, version : String) : Boolean
}
