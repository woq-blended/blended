package blended.mgmt.ui.components.filter

import blended.updater.config.ContainerInfo
import java.util.regex.Pattern
import scala.collection.immutable.Seq
import blended.mgmt.ui.util.Logger

object ContainerInfoFilter {

  private[this] val log = Logger[ContainerInfoFilter.type]

  case class ContainerId(containerId: String, exact: Boolean = false) extends Filter[ContainerInfo] {
    override def matches(containerInfo: ContainerInfo): Boolean =
      if (exact) containerInfo.containerId == containerId
      else containerInfo.containerId.contains(containerId)
  }

  case class ContainsContainerProperty(property: String) extends Filter[ContainerInfo] {
    override def matches(containerInfo: ContainerInfo): Boolean = containerInfo.properties.exists(p => p._1 == property)
  }
  
  case class Property(name: String, value: String, exact: Boolean = false) extends Filter[ContainerInfo] {
    override def matches(containerInfo: ContainerInfo): Boolean = 
      containerInfo.properties.exists(p => p._1 == name && (if(exact) p._2 == value else value.contains( p._2)))
  }

  case class FreeText(text: String) extends Filter[ContainerInfo] {
    override def matches(containerInfo: ContainerInfo): Boolean = {
      // create (lazily) text lines that will be searched
      val lazyLines = Stream(
        () => Seq(containerInfo.containerId),
        () => containerInfo.properties.values,
        () => containerInfo.profiles.map(p => p.name + "-" + p.version),
        () => containerInfo.profiles.flatMap(p => p.overlays.flatMap(o => o.overlays).map(_.toString()))
      ).flatMap { generator => generator() }

      val p = Pattern.compile(Pattern.quote(text))
      lazyLines.exists { line =>
        log.trace("Match? line = " + line + ", " + this)
        p.matcher(line).find()
      }
    }

  }

}