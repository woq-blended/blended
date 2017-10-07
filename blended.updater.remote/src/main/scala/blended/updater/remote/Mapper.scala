package blended.updater.remote

import blended.updater.config.{ Mapper => BaseMapper }
import scala.collection.JavaConverters._
import scala.util.Try

trait Mapper extends BaseMapper {

  def mapContainerState(containerState: ContainerState): java.util.Map[String, AnyRef] = {
    Map[String, AnyRef](
      "containerId" -> containerState.containerId,
      "outstandingActions" -> containerState.outstandingActions.map(a => mapUpdateAction(a)).asJava,
      "profiles" -> containerState.profiles.map(p => mapProfile(p)).asJava,
      "syncTimeStamp" -> containerState.syncTimeStamp.map(l => java.lang.Long.valueOf(l)).orNull
    ).asJava
  }

  def unmapContainerState(map: AnyRef): Try[ContainerState] = Try {
    val p = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    ContainerState(
      containerId = p("containerId").asInstanceOf[String],
      outstandingActions = p("outstandingActions").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(a => unmapUpdateAction(a).get),
      profiles = p("profiles").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(p => unmapProfile(p).get),
      syncTimeStamp = Option(p("syncTimeStamp").asInstanceOf[java.lang.Long]).map(_.longValue())
    )
  }

}

object Mapper extends Mapper