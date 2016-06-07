package blended.mgmt.base

trait FrameworkService {

  def restartContainer(reason: String) : Unit

}
