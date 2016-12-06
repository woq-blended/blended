package blended.updater.remote

import blended.persistence.PersistenceService

import scala.collection.JavaConverters._
import scala.util.Try

import blended.updater.config._
import com.typesafe.config.ConfigFactory

class PersistentContainerStatePersistor(persistenceService: PersistenceService) extends ContainerStatePersistor {

  val pClassName = "ContainerState"

  def fromContainerState(containerState: ContainerState): java.util.Map[String, AnyRef] = {
    Map(
      "containerId" -> containerState.containerId,
      "outstandingActions" -> containerState.outstandingActions.map {
        case a: AddRuntimeConfig =>
          Map(
            "runtimeConfig" -> RuntimeConfigCompanion.toConfig(a.runtimeConfig).root().unwrapped()
          ).asJava
        case a: AddOverlayConfig =>
          Map(
            "overlay" -> OverlayConfigCompanion.toConfig(a.overlay).root().unwrapped()
          ).asJava
        case s: StageProfile =>
          Map(
            "profileName" -> s.profileName,
            "profileVersion" -> s.profileVersion,
            "overlays" -> s.overlays.map { o =>
              Map("name" -> o.name,
                "version" -> o.version
              ).asJava
            }.asJava
          ).asJava
        case a: ActivateProfile =>
          Map(
            "profileName" -> a.profileName,
            "profileVersion" -> a.profileVersion,
            "overlays" -> a.overlays.map { o =>
              Map("name" -> o.name,
                "version" -> o.version
              ).asJava
            }.asJava
          ).asJava
      }.asJava,
      "profiles" -> containerState.profiles.map { p =>
        Map(
          "name" -> p.name,
          "version" -> p.version,
          "overlays" -> p.overlays.map { o =>
            Map(
              "overlays" -> o.overlays.map { o =>
                Map(
                  "name" -> o.name,
                  "version" -> o.version
                ).asJava
              }.asJava,
              "state" -> o.state,
              "reason" -> o.reason
            ).asJava
          }.asJava
        ).asJava
      }.asJava,
      "syncTimeStamp" -> containerState.syncTimeStamp.map(l => java.lang.Long.valueOf(l)).orNull).asJava
  }

  def toContainerState(map: java.util.Map[String, _ <: AnyRef]): Try[ContainerState] = Try {
    val data = map.asScala
    ContainerState(
      containerId = data.get("containerId").asInstanceOf[String],
      outstandingActions = data.get("outstandingActions").asInstanceOf[java.util.Collection[_]].asScala.map { a =>
        val aData = a.asInstanceOf[java.util.Map[String, _]].asScala

        def pName = aData.get("profileName").asInstanceOf[String]
        def pVersion = aData.get("profileVersion").asInstanceOf[String]
        def pOverlays = aData.get("overlays").asInstanceOf[java.util.Collection[_]].asScala.map { o =>
          val oData = o.asInstanceOf[java.util.Map[String, _]].asScala
          OverlayRef(
            name = oData.get("name").asInstanceOf[String],
            version = oData.get("version").asInstanceOf[String]
          )
        }.toList

        aData.get("kind").asInstanceOf[String] match {
          case kind @ UpdateAction.KindAddRuntimeConfig =>
            AddRuntimeConfig(
              runtimeConfig = RuntimeConfigCompanion.read(
                ConfigFactory.parseMap(a.asInstanceOf[java.util.Map[String, _]])
              ).get
            )
          case kind @ UpdateAction.KindAddOverlayConfig =>
            AddOverlayConfig(
              overlay = OverlayConfigCompanion.read(
                ConfigFactory.parseMap(a.asInstanceOf[java.util.Map[String, _]])
              ).get
            )
          case kind @ UpdateAction.KindStageProfile =>
            StageProfile(
              profileName = pName,
              profileVersion = pVersion,
              overlays = pOverlays
            )
          case kind @ UpdateAction.KindActivateProfile =>
            ActivateProfile(
              profileName = pName,
              profileVersion = pVersion,
              overlays = pOverlays
            )
          case k => error("Unsupported kind: " + k)
        }
      }.toList,
      profiles = data.get("profiles").asInstanceOf[java.util.Collection[_]].asScala.map { p =>
        ???
      }.toList,
      syncTimeStamp = data.get("syncTimeStamp").map(_.asInstanceOf[Long])
    )
  }

  override def findAllContainerStates(): List[ContainerState] = {
    val state = persistenceService.findAll(pClassName)
    state.flatMap(s => toContainerState(s).toOption).toList
  }

  override def findContainerState(containerId: String): Option[ContainerState] = {
    // TODO: ensure, we take the newest
    val state = persistenceService.findByExample(pClassName, Map("containerId" -> containerId).asJava)
    state.flatMap(s => toContainerState(s).toOption).headOption
  }

  override def updateContainerState(containerState: ContainerState): Unit = {
    persistenceService.persist(pClassName, fromContainerState(containerState))
  }
}