package blended.updater.config

import org.scalacheck.Arbitrary.{arbitrary, _}
import org.scalacheck.{Arbitrary, Gen}

trait TestData {

  val artifacts = for {
    fileName <- arbitrary[String]
    sha1Sum <- arbitrary[Option[String]]
  } yield Artifact(
    url = s"http://fakeurl/${fileName}",
    fileName = Gen.oneOf(Some(fileName), None).sample.flatten,
    sha1Sum = sha1Sum
  )
  implicit val arbArtifact : Arbitrary[Artifact] = Arbitrary(artifacts)

  val bundleConfigs = for {
    artifact <- arbitrary[Artifact]
    start <- arbitrary[Boolean]
    startLevel <- arbitrary[Option[Int]]
  } yield BundleConfig(artifact, start, startLevel)

  implicit val arbBundleConfig : Arbitrary[BundleConfig] = Arbitrary(bundleConfigs)

  val featureRefs = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    url <- arbitrary[Option[String]]
  } yield FeatureRef(name, version, url)
  implicit val arbFeatureRefs : Arbitrary[FeatureRef] = Arbitrary(featureRefs)

  val featurConfigs = for {
    featureRef <- arbitrary[FeatureRef]
    bundles <- arbitrary[List[BundleConfig]]
    features <- arbitrary[List[FeatureRef]]
  } yield FeatureConfig(featureRef.name, featureRef.version, featureRef.url, bundles, features)
  implicit val arbFeatureConfig : Arbitrary[FeatureConfig] = Arbitrary(featurConfigs)

  val generatedConfigs = for {
    configFile <- arbitrary[String]
    config <- arbitrary[String]
  } yield GeneratedConfig(configFile, config)
  implicit val arbGeneratedConfig : Arbitrary[GeneratedConfig] = Arbitrary(generatedConfigs)

  val overlayRefs = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
  } yield OverlayRef(name, version)
  implicit val arbOverlayRef : Arbitrary[OverlayRef] = Arbitrary(overlayRefs)

  val overlayConfigs = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    generatedConfigs <- arbitrary[List[GeneratedConfig]]
    properties <- arbitrary[Map[String, String]]
  } yield OverlayConfig(name, version, generatedConfigs, properties)
  implicit val arbOverlayConfig : Arbitrary[OverlayConfig] = Arbitrary(overlayConfigs)

  val runtimeConfigs = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    bundles <- arbitrary[List[BundleConfig]]
    startLevel <- arbitrary[Int]
    defaultStartLevel <- arbitrary[Int]
    properties <- arbitrary[Map[String, String]]
    frameworkProperties <- arbitrary[Map[String, String]]
    systemProperties <- arbitrary[Map[String, String]]
    features <- arbitrary[List[FeatureRef]]
    resources <- arbitrary[List[Artifact]]
    resolvedFeatures <- arbitrary[List[FeatureConfig]]
  } yield RuntimeConfig(
    name,
    version,
    bundles,
    startLevel,
    defaultStartLevel,
    properties,
    frameworkProperties,
    systemProperties,
    features,
    resources,
    resolvedFeatures
  )
  implicit val arbRuntimeConfigs : Arbitrary[RuntimeConfig] = Arbitrary(runtimeConfigs)

  val addRuntimeConfigs = for {
    id <- arbitrary[String]
    runtimeConfig <- arbitrary[RuntimeConfig]
  } yield AddRuntimeConfig(id, runtimeConfig)

  val addOverlayConfigs = for {
    id <- arbitrary[String]
    overlay <- arbitrary[OverlayConfig]
  } yield AddOverlayConfig(id, overlay)

  val stageProfiles = for {
    id <- arbitrary[String]
    profileName <- arbitrary[String]
    profileVersion <- arbitrary[String]
    overlays <- arbitrary[Set[OverlayRef]]
  } yield StageProfile(id, profileName, profileVersion, overlays)

  val activateProfiles = for {
    id <- arbitrary[String]
    profileName <- arbitrary[String]
    profileVersion <- arbitrary[String]
    overlays <- arbitrary[Set[OverlayRef]]
  } yield ActivateProfile(id, profileName, profileVersion, overlays)

  val updateActions : Gen[UpdateAction] = Gen.oneOf(
    addRuntimeConfigs,
    addOverlayConfigs,
    stageProfiles,
    activateProfiles
  )
  implicit val arbUpdateAction : Arbitrary[UpdateAction] = Arbitrary(updateActions)

  val overlayStates = Gen.oneOf[OverlayState](OverlayState.Active, OverlayState.Valid, OverlayState.Invalid, OverlayState.Pending)
  implicit val arbOverlayState : Arbitrary[OverlayState] = Arbitrary(overlayStates)

  val overlaySets = for {
    overlays <- arbitrary[Set[OverlayRef]]
    state <- arbitrary[OverlayState]
    reason <- arbitrary[Option[String]]
  } yield OverlaySet(overlays, state, reason)
  implicit val arbOverlaySets : Arbitrary[OverlaySet] = Arbitrary(overlaySets)

  val serviceInfos = for {
    name <- arbitrary[String]
    serviceType <- arbitrary[String]
    //    timestamp <- arbitrary[ju.Date]
    timestampMsec <- Gen.choose(10000, Long.MaxValue)
    lifetimeMsec <- Gen.choose(0, Long.MaxValue) // arbitrary[Long].filter(_ >= 0)
    props <- arbitrary[Map[String, String]]
  } yield ServiceInfo(name, serviceType, timestampMsec, lifetimeMsec, props)
  implicit val arbServiceInfo : Arbitrary[ServiceInfo] = Arbitrary(serviceInfos)

  val profileGroups = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    overlays <- arbitrary[List[OverlaySet]] if overlays.nonEmpty
  } yield ProfileGroup(name, version, overlays)
  implicit val arbProfileGroup : Arbitrary[ProfileGroup] = Arbitrary(profileGroups)

  val profiles = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    overlaySet <- arbitrary[OverlaySet]
  } yield Profile(name, version, overlaySet)
  implicit val arbProfile : Arbitrary[Profile] = Arbitrary(profiles)

  val containerInfos = for {
    containerId <- arbitrary[String]
    properties <- arbitrary[Map[String, String]]
    serviceInfos <- arbitrary[List[ServiceInfo]]
    profiles <- arbitrary[List[Profile]]
    //    timestamp <- arbitrary[ju.Date]
    timestampMsec <- Gen.choose(10000, Long.MaxValue)
    appliedUpdateActionIds <- arbitrary[List[String]]
  } yield ContainerInfo(containerId, properties, serviceInfos, profiles, timestampMsec, appliedUpdateActionIds)
  implicit val arbContainerInfo : Arbitrary[ContainerInfo] = Arbitrary(containerInfos)

  val remoteContainerStates = for {
    containerInfo <- arbitrary[ContainerInfo]
    outstandingUpdateActions <- arbitrary[List[UpdateAction]]
  } yield RemoteContainerState(containerInfo, outstandingUpdateActions)
  implicit val arbRemoteContainerState : Arbitrary[RemoteContainerState] = Arbitrary(remoteContainerStates)

  val rolloutProfiles = for {
    profileName <- arbitrary[String]
    profileVersion <- arbitrary[String]
    overlays <- arbitrary[Set[OverlayRef]]
    containerIds <- arbitrary[List[String]]
  } yield RolloutProfile(profileName, profileVersion, overlays, containerIds)
  implicit val arbRolloutProfile : Arbitrary[RolloutProfile] = Arbitrary(rolloutProfiles)

}

object TestData extends TestData
