package blended.mgmt.ui.components.filter

import blended.updater.config.ContainerInfo
import java.util.regex.Pattern
import scala.collection.immutable.Seq
import blended.mgmt.ui.util.Logger

object ContainerInfoFilter {

  private[this] val log = Logger[ContainerInfoFilter.type]

  case class ContainerId(containerId: String) extends Filter[ContainerInfo] {
    override def matches(containerInfo: ContainerInfo): Boolean = containerInfo.containerId == containerId
  }

  case class ContainsContainerProperty(property: String) extends Filter[ContainerInfo] {
    override def matches(containerInfo: ContainerInfo): Boolean = containerInfo.properties.exists(p => p._1 == property)
  }

  case class FreeText(text: String) extends Filter[ContainerInfo] {
    override def matches(containerInfo: ContainerInfo): Boolean = {
      val lazyLines = Stream(
        () => Seq(containerInfo.containerId)
      ).flatMap { generator => generator() }

      val p = Pattern.compile(Pattern.quote(text))
      lazyLines.exists { line =>
        log.trace("Match? line = " + line + ", pattern = " + p)
        p.matcher(line).find()
      }
    }

  }

}