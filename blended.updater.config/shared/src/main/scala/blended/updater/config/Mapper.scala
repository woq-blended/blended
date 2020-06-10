package blended.updater.config

import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * Mapper functions for bi-directional mapping of domain model case classes to [[java.util.Map]]'s with JVM-only types.
 */
trait Mapper {

  def mapArtifact(a: Artifact): java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "url" -> a.url,
      "fileName" -> a.fileName.orNull,
      "sha1Sum" -> a.sha1Sum.orNull
    ).asJava

  def mapBundleConfig(b: BundleConfig): java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "artifact" -> mapArtifact(b.artifact),
      "start" -> java.lang.Boolean.valueOf(b.start),
      "startLevel" -> b.startLevel.map(i => java.lang.Integer.valueOf(i)).orNull
    ).asJava

  def mapFeatureRef(ref: FeatureRef): java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> ref.name,
      "version" -> ref.version,
      "url" -> ref.url.orNull
    ).asJava

  def mapFeatureConfig(f: FeatureConfig): java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> f.name,
      "version" -> f.version,
      "url" -> f.url.orNull,
      "features" -> f.features.map { f =>
        mapFeatureRef(f)
      }.asJava,
      "bundles" -> f.bundles.map { b =>
        mapBundleConfig(b)
      }.asJava
    ).asJava

  def mapProfile(profile: Profile): java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> profile.name,
      "version" -> profile.version,
      "bundles" -> profile.bundles.map { b =>
        mapBundleConfig(b)
      }.asJava,
      "startLevel" -> java.lang.Integer.valueOf(profile.startLevel),
      "defaultStartLevel" -> java.lang.Integer.valueOf(profile.defaultStartLevel),
      "properties" -> profile.properties.asJava,
      "frameworkProperties" -> profile.frameworkProperties.asJava,
      "systemProperties" -> profile.systemProperties.asJava,
      "features" -> profile.features.map(f => mapFeatureRef(f)).asJava,
      "resources" -> profile.resources.map(a => mapArtifact(a)).asJava,
      "resolvedFeatures" -> profile.resolvedFeatures.map(f => mapFeatureConfig(f)).asJava
    ).asJava

  def mapRemoteContainerState(s: RemoteContainerState): java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "containerInfo" -> mapContainerInfo(s.containerInfo)
    ).asJava

  def mapServiceInfo(si: ServiceInfo): java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> si.name,
      "serviceType" -> si.serviceType,
      "timestampMsec" -> java.lang.Long.valueOf(si.timestampMsec),
      "lifetimeMsec" -> java.lang.Long.valueOf(si.lifetimeMsec),
      "props" -> si.props.asJava
    ).asJava

  def mapContainerInfo(ci: ContainerInfo): java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "containerId" -> ci.containerId,
      "properties" -> ci.properties.asJava,
      "serviceInfos" -> ci.serviceInfos.map(si => mapServiceInfo(si)).asJava,
      "profiles" -> ci.profiles.map(p => mapProfileRef(p)).asJava,
      "timestampMsec" -> java.lang.Long.valueOf(ci.timestampMsec)
    ).asJava


  def mapGeneratedConfig(c: GeneratedConfig): java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "configFile" -> c.configFile,
      "config" -> c.config
    ).asJava

  def mapProfileRef(p: ProfileRef): java.util.Map[String, AnyRef] =
    Map[String, AnyRef](
      "name" -> p.name,
      "version" -> p.version
    ).asJava

  def unmapRemoteContainerState(map: AnyRef): Try[RemoteContainerState] = Try {
    val m = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    RemoteContainerState(
      containerInfo = unmapContainerInfo(m("containerInfo")).get
    )
  }

  def unmapArtifact(map: AnyRef): Try[Artifact] = Try {
    val m = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    Artifact(
      url = m("url").asInstanceOf[String],
      fileName = m.get("fileName").flatMap(f => Option(f.asInstanceOf[String])),
      sha1Sum = m.get("sha1Sum").flatMap(s => Option(s.asInstanceOf[String]))
    )
  }

  def unmapContainerInfo(map: AnyRef): Try[ContainerInfo] = Try {
    val ci = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    ContainerInfo(
      containerId = ci("containerId").asInstanceOf[String],
      properties = ci("properties").asInstanceOf[java.util.Map[String, String]].asScala.toMap,
      serviceInfos = ci("serviceInfos")
        .asInstanceOf[java.util.Collection[AnyRef]]
        .asScala
        .toList
        .map(si => unmapServiceInfo(si).get),
      profiles =
        ci("profiles").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(p => unmapProfileRef(p).get),
      timestampMsec = ci("timestampMsec").asInstanceOf[java.lang.Long].longValue()
    )
  }

  def unmapServiceInfo(map: AnyRef): Try[ServiceInfo] = Try {
    val si = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    ServiceInfo(
      name = si("name").asInstanceOf[String],
      serviceType = si("serviceType").asInstanceOf[String],
      timestampMsec = si("timestampMsec").asInstanceOf[Long].longValue(),
      lifetimeMsec = si("lifetimeMsec").asInstanceOf[Long].longValue(),
      props = si("props").asInstanceOf[java.util.Map[String, String]].asScala.toMap
    )
  }

  def unmapProfileRef(map: AnyRef): Try[ProfileRef] = Try {
    val p = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    ProfileRef(
      name = p("name").asInstanceOf[String],
      version = p("version").asInstanceOf[String]
    )
  }

  def unmapProfile(map: AnyRef): Try[Profile] = Try {
    val rc = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    Profile(
      name = rc("name").asInstanceOf[String],
      version = rc("version").asInstanceOf[String],
      bundles =
        rc("bundles").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(b => unmapBundleConfig(b).get),
      startLevel = rc("startLevel").asInstanceOf[java.lang.Integer].intValue(),
      defaultStartLevel = rc("defaultStartLevel").asInstanceOf[java.lang.Integer].intValue(),
      properties = rc("properties").asInstanceOf[java.util.Map[String, String]].asScala.toMap,
      frameworkProperties = rc("frameworkProperties").asInstanceOf[java.util.Map[String, String]].asScala.toMap,
      systemProperties = rc("systemProperties").asInstanceOf[java.util.Map[String, String]].asScala.toMap,
      features =
        rc("features").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(f => unmapFeatureRef(f).get),
      resources =
        rc("resources").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(a => unmapArtifact(a).get),
      resolvedFeatures = rc("resolvedFeatures")
        .asInstanceOf[java.util.Collection[AnyRef]]
        .asScala
        .toList
        .map(f => unmapFeatureConfig(f).get)
    )
  }

  def unmapGeneratedConfig(map: AnyRef): Try[GeneratedConfig] = Try {
    val c = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    GeneratedConfig(
      configFile = c("configFile").asInstanceOf[String],
      config = c("config").asInstanceOf[String]
    )
  }

  def unmapBundleConfig(map: AnyRef): Try[BundleConfig] = Try {
    val bc = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    BundleConfig(
      artifact = unmapArtifact(bc("artifact")).get,
      start = bc("start").asInstanceOf[java.lang.Boolean],
      startLevel = Option(bc("startLevel").asInstanceOf[java.lang.Integer]).map(_.intValue())
    )
  }

  def unmapFeatureRef(map: AnyRef): Try[FeatureRef] = Try {
    val f = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    FeatureRef(
      name = f("name").asInstanceOf[String],
      version = f("version").asInstanceOf[String],
      url = Option(f("url").asInstanceOf[String])
    )
  }

  def unmapFeatureConfig(map: AnyRef): Try[FeatureConfig] = Try {
    val f = map.asInstanceOf[java.util.Map[String, AnyRef]].asScala
    FeatureConfig(
      name = f("name").asInstanceOf[String],
      version = f("version").asInstanceOf[String],
      url = Option(f("url").asInstanceOf[String]),
      bundles =
        f("bundles").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(b => unmapBundleConfig(b).get),
      features =
        f("features").asInstanceOf[java.util.Collection[AnyRef]].asScala.toList.map(f => unmapFeatureRef(f).get)
    )
  }

}

object Mapper extends Mapper
