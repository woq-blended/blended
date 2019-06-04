package blended.updater.config

import scala.collection.JavaConverters._
import scala.util.Try

/**
 * Mapper functions for bi-directional mapping of domain model case classes to [[java.util.Map]]'s with JVM-only types.
 */
trait Mapper {

  def mapArtifact(a : Artifact) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "url" -> a.url,
      "fileName" -> a.fileName.orNull,
      "sha1Sum" -> a.sha1Sum.orNull
    ).asJava

  def mapBundleConfig(b : BundleConfig) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "artifact" -> mapArtifact(b.artifact),
      "start" -> java.lang.Boolean.valueOf(b.start),
      "startLevel" -> b.startLevel.map(i => java.lang.Integer.valueOf(i)).orNull
    ).asJava

  def mapFeatureRef(ref : FeatureRef) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> ref.name,
      "version" -> ref.version,
      "url" -> ref.url.orNull
    ).asJava

  def mapFeatureConfig(f : FeatureConfig) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> f.name,
      "version" -> f.version,
      "url" -> f.url.orNull,
      "features" -> f.features.map { f => mapFeatureRef(f) }.asJava,
      "bundles" -> f.bundles.map { b => mapBundleConfig(b) }.asJava
    ).asJava

  def mapRuntimeConfig(runtimeConfig : RuntimeConfig) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> runtimeConfig.name,
      "version" -> runtimeConfig.version,
      "bundles" -> runtimeConfig.bundles.map { b => mapBundleConfig(b) }.asJava,
      "startLevel" -> java.lang.Integer.valueOf(runtimeConfig.startLevel),
      "defaultStartLevel" -> java.lang.Integer.valueOf(runtimeConfig.defaultStartLevel),
      "properties" -> runtimeConfig.properties.asJava,
      "frameworkProperties" -> runtimeConfig.frameworkProperties.asJava,
      "systemProperties" -> runtimeConfig.systemProperties.asJava,
      "features" -> runtimeConfig.features.map(f => mapFeatureRef(f)).asJava,
      "resources" -> runtimeConfig.resources.map(a => mapArtifact(a)).asJava,
      "resolvedFeatures" -> runtimeConfig.resolvedFeatures.map(f => mapFeatureConfig(f)).asJava
    ).asJava

  def mapRemoteContainerState(s : RemoteContainerState) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "containerInfo" -> mapContainerInfo(s.containerInfo),
      "outstandingUpdateActions" -> s.outstandingUpdateActions.map(a => mapUpdateAction(a)).asJava
    ).asJava

  def mapServiceInfo(si : ServiceInfo) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> si.name,
      "serviceType" -> si.serviceType,
      "timestampMsec" -> java.lang.Long.valueOf(si.timestampMsec),
      "lifetimeMsec" -> java.lang.Long.valueOf(si.lifetimeMsec),
      "props" -> si.props.asJava
    ).asJava

  def mapContainerInfo(ci : ContainerInfo) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "containerId" -> ci.containerId,
      "properties" -> ci.properties.asJava,
      "serviceInfos" -> ci.serviceInfos.map(si => mapServiceInfo(si)).asJava,
      "profiles" -> ci.profiles.map(p => mapProfile(p)).asJava,
      "timestampMsec" -> java.lang.Long.valueOf(ci.timestampMsec)
    ).asJava

  def mapUpdateAction(a : UpdateAction) : java.util.Map[String, AnyRef] = a match {
    case AddRuntimeConfig(id, rc) =>
      Map(
        "kind" -> UpdateAction.KindAddRuntimeConfig,
        "id" -> id,
        "runtimeConfig" -> mapRuntimeConfig(rc)
      ).asJava
    case AddOverlayConfig(id, oc) =>
      Map(
        "kind" -> UpdateAction.KindAddOverlayConfig,
        "id" -> id,
        "overlay" -> mapOverlayConfig(oc)
      ).asJava
    case ActivateProfile(id, profileName, profileVersion, overlays) =>
      Map(
        "kind" -> UpdateAction.KindActivateProfile,
        "id" -> id,
        "profileName" -> profileName,
        "profileVersion" -> profileVersion,
        "overlays" -> overlays.map(o => mapOverlayRef(o)).asJava
      ).asJava
    case StageProfile(id, profileName, profileVersion, overlays) =>
      Map(
        "kind" -> UpdateAction.KindStageProfile,
        "id" -> id,
        "profileName" -> profileName,
        "profileVersion" -> profileVersion,
        "overlays" -> overlays.map(o => mapOverlayRef(o)).asJava
      ).asJava
  }

  def mapOverlayConfig(oc : OverlayConfig) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> oc.name,
      "version" -> oc.version,
      "generatedConfigs" -> oc.generatedConfigs.map(c => mapGeneratedConfig(c)).asJava,
      "properties" -> oc.properties.asJava
    ).asJava

  def mapGeneratedConfig(c : GeneratedConfig) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "configFile" -> c.configFile,
      "config" -> c.config
    ).asJava

  def mapProfileGroup(p : ProfileGroup) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> p.name,
      "version" -> p.version,
      "overlays" -> p.overlays.map(o => mapOverlaySet(o)).asJava
    ).asJava

  def mapProfile(p : Profile) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> p.name,
      "version" -> p.version,
      "overlays" -> mapOverlaySet(p.overlaySet)
    ).asJava

  def mapOverlaySet(o : OverlaySet) : java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "overlays" -> o.overlays.map(o => mapOverlayRef(o)).asJava,
      "state" -> o.state.state,
      "reason" -> o.reason.orNull
    ).asJava

  def mapOverlayRef(o : OverlayRef) =
    Map[String, AnyRef](
      "name" -> o.name,
      "version" -> o.version
    ).asJava

  def unmapRemoteContainerState(map : AnyRef) : Try[RemoteContainerState] = Try {
    val m = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    RemoteContainerState(
      containerInfo = unmapContainerInfo(m("containerInfo")).get,
      outstandingUpdateActions = m("outstandingUpdateActions").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(a => unmapUpdateAction(a).get)
    )
  }

  def unmapArtifact(map : AnyRef) : Try[Artifact] = Try {
    val m = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    Artifact(
      url = m("url").asInstanceOf[String],
      fileName = m.get("fileName").flatMap(f => Option(f.asInstanceOf[String])),
      sha1Sum = m.get("sha1Sum").flatMap(s => Option(s.asInstanceOf[String]))
    )
  }

  def unmapUpdateAction(map : AnyRef) : Try[UpdateAction] = Try {
    val a = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    a("kind") match {
      case UpdateAction.KindAddRuntimeConfig =>
        AddRuntimeConfig(
          id = a("id").asInstanceOf[String],
          runtimeConfig = unmapRuntimeConfig(a("runtimeConfig")).get
        )
      case UpdateAction.KindAddOverlayConfig =>
        AddOverlayConfig(
          id = a("id").asInstanceOf[String],
          overlay = unmapOverlayConfig(a("overlay")).get
        )
      case UpdateAction.KindActivateProfile =>
        ActivateProfile(
          id = a("id").asInstanceOf[String],
          profileName = a("profileName").asInstanceOf[String],
          profileVersion = a("profileVersion").asInstanceOf[String],
          overlays = a("overlays").asInstanceOf[java.util.Collection[AnyRef]].asScala.map(o => unmapOverlayRef(o).get).toSet
        )
      case UpdateAction.KindStageProfile =>
        StageProfile(
          id = a("id").asInstanceOf[String],
          profileName = a("profileName").asInstanceOf[String],
          profileVersion = a("profileVersion").asInstanceOf[String],
          overlays = a("overlays").asInstanceOf[java.util.Collection[AnyRef]].asScala.map(o => unmapOverlayRef(o).get).toSet
        )
      case kind => sys.error(s"Unsupported update action kind: ${kind}")
    }
  }

  def unmapContainerInfo(map : AnyRef) : Try[ContainerInfo] = Try {
    val ci = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    ContainerInfo(
      containerId = ci("containerId").asInstanceOf[String],
      properties = ci("properties").asInstanceOf[java.util.Map[String, String]].asScala.toMap,
      serviceInfos = ci("serviceInfos").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(si => unmapServiceInfo(si).get),
      profiles = ci("profiles").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(p => unmapProfile(p).get),
      timestampMsec = ci("timestampMsec").asInstanceOf[java.lang.Long].longValue(),
      appliedUpdateActionIds = Nil
    )
  }

  def unmapServiceInfo(map : AnyRef) : Try[ServiceInfo] = Try {
    val si = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    ServiceInfo(
      name = si("name").asInstanceOf[String],
      serviceType = si("serviceType").asInstanceOf[String],
      timestampMsec = si("timestampMsec").asInstanceOf[Long].longValue(),
      lifetimeMsec = si("lifetimeMsec").asInstanceOf[Long].longValue(),
      props = si("props").asInstanceOf[java.util.Map[String, String]].asScala.toMap
    )
  }

  def unmapProfileGroup(map : AnyRef) : Try[ProfileGroup] = Try {
    val p = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    ProfileGroup(
      name = p("name").asInstanceOf[String],
      version = p("version").asInstanceOf[String],
      overlays = p("overlays").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(o => unmapOverlaySet(o).get)
    )
  }

  def unmapProfile(map : AnyRef) : Try[Profile] = Try {
    val p = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    Profile(
      name = p("name").asInstanceOf[String],
      version = p("version").asInstanceOf[String],
      overlaySet = unmapOverlaySet(p("overlays")).get
    )
  }

  def unmapOverlayRef(map : AnyRef) : Try[OverlayRef] = Try {
    val or = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    OverlayRef(
      name = or("name").asInstanceOf[String],
      version = or("version").asInstanceOf[String]
    )
  }

  def unmapOverlaySet(map : AnyRef) : Try[OverlaySet] = Try {
    val o = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    OverlaySet(
      overlays = o("overlays").asInstanceOf[java.util.Collection[AnyRef]].asScala.map(o => unmapOverlayRef(o).get).toSet,
      state = {
        val s = o("state").asInstanceOf[String]
        OverlayState.fromString(s).getOrElse(throw new RuntimeException("Unsupported overlay state: " + s))
      },
      reason = Option(o("reason").asInstanceOf[String])
    )
  }

  def unmapOverlayConfig(map : AnyRef) : Try[OverlayConfig] = Try {
    val oc = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    OverlayConfig(
      name = oc("name").asInstanceOf[String],
      version = oc("version").asInstanceOf[String],
      generatedConfigs = oc("generatedConfigs").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(c => unmapGeneratedConfig(c).get),
      properties = oc("properties").asInstanceOf[java.util.Map[String, String]].asScala.toMap
    )
  }

  def unmapRuntimeConfig(map : AnyRef) : Try[RuntimeConfig] = Try {
    val rc = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    RuntimeConfig(
      name = rc("name").asInstanceOf[String],
      version = rc("version").asInstanceOf[String],
      bundles = rc("bundles").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(b => unmapBundleConfig(b).get),
      startLevel = rc("startLevel").asInstanceOf[java.lang.Integer].intValue(),
      defaultStartLevel = rc("defaultStartLevel").asInstanceOf[java.lang.Integer].intValue(),
      properties = rc("properties").asInstanceOf[java.util.Map[String, String]].asScala.toMap,
      frameworkProperties = rc("frameworkProperties").asInstanceOf[java.util.Map[String, String]].asScala.toMap,
      systemProperties = rc("systemProperties").asInstanceOf[java.util.Map[String, String]].asScala.toMap,
      features = rc("features").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(f => unmapFeatureRef(f).get),
      resources = rc("resources").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(a => unmapArtifact(a).get),
      resolvedFeatures = rc("resolvedFeatures").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(f => unmapFeatureConfig(f).get)
    )
  }

  def unmapGeneratedConfig(map : AnyRef) : Try[GeneratedConfig] = Try {
    val c = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    GeneratedConfig(
      configFile = c("configFile").asInstanceOf[String],
      config = c("config").asInstanceOf[String]
    )
  }

  def unmapBundleConfig(map : AnyRef) : Try[BundleConfig] = Try {
    val bc = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    BundleConfig(
      artifact = unmapArtifact(bc("artifact")).get,
      start = bc("start").asInstanceOf[java.lang.Boolean],
      startLevel = Option(bc("startLevel").asInstanceOf[java.lang.Integer]).map(_.intValue())

    )
  }

  def unmapFeatureRef(map : AnyRef) : Try[FeatureRef] = Try {
    val f = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    FeatureRef(
      name = f("name").asInstanceOf[String],
      version = f("version").asInstanceOf[String],
      url = Option(f("url").asInstanceOf[String])
    )
  }

  def unmapFeatureConfig(map : AnyRef) : Try[FeatureConfig] = Try {
    val f = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    FeatureConfig(
      name = f("name").asInstanceOf[String],
      version = f("version").asInstanceOf[String],
      url = Option(f("url").asInstanceOf[String]),
      bundles = f("bundles").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(b => unmapBundleConfig(b).get),
      features = f("features").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(f => unmapFeatureRef(f).get)
    )
  }

}

object Mapper extends Mapper
