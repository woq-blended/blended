package blended.updater.config

import java.{util => ju}

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary._

trait TestData {

  val artifacts = for {
    fileName <- arbitrary[String]
    sha1Sum <- arbitrary[Option[String]]
  } yield Artifact(
    url = s"http://fakeurl/${fileName}",
    fileName = Gen.oneOf(Some(fileName), None).sample.flatten,
    sha1Sum = sha1Sum
  )
  implicit val arbArtifact: Arbitrary[Artifact] = Arbitrary(artifacts)

  val bundleConfigs = for {
    artifact <- arbitrary[Artifact]
    start <- arbitrary[Boolean]
    startLevel <- arbitrary[Option[Int]]
  } yield BundleConfig(artifact, start, startLevel)

  implicit val arbBundleConfig: Arbitrary[BundleConfig] = Arbitrary(bundleConfigs)

  val featureRefs = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    url <- arbitrary[Option[String]]
  } yield FeatureRef(name, version, url)
  implicit val arbFeatureRefs: Arbitrary[FeatureRef] = Arbitrary(featureRefs)

  val featurConfigs = for {
    featureRef <- arbitrary[FeatureRef]
    bundles <- arbitrary[List[BundleConfig]]
    features <- arbitrary[List[FeatureRef]]
  } yield FeatureConfig(featureRef.name, featureRef.version, featureRef.url, bundles, features)
  implicit val arbFeatureConfig: Arbitrary[FeatureConfig] = Arbitrary(featurConfigs)

  val generatedConfigs = for {
    configFile <- arbitrary[String]
    config <- arbitrary[String]
  } yield GeneratedConfig(configFile, config)
  implicit val arbGeneratedConfig: Arbitrary[GeneratedConfig] = Arbitrary(generatedConfigs)

  val overlayRefs = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
  } yield OverlayRef(name, version)
  implicit val arbOverlayRef: Arbitrary[OverlayRef] = Arbitrary(overlayRefs)

  val overlayConfigs = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    generatedConfigs <- arbitrary[List[GeneratedConfig]]
    properties <- arbitrary[Map[String, String]]
  } yield OverlayConfig(name, version, generatedConfigs, properties)
  implicit val arbOverlayConfig: Arbitrary[OverlayConfig] = Arbitrary(overlayConfigs)

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
  implicit val arbRuntimeConfigs: Arbitrary[RuntimeConfig] = Arbitrary(runtimeConfigs)

  val addRuntimeConfigs = for {
    runtimeConfig <- arbitrary[RuntimeConfig]
  } yield AddRuntimeConfig(runtimeConfig)

  val addOverlayConfigs = for {
    overlay <- arbitrary[OverlayConfig]
  } yield AddOverlayConfig(overlay)

  val stageProfiles = for {
    profileName <- arbitrary[String]
    profileVersion <- arbitrary[String]
    overlays <- arbitrary[List[OverlayRef]]
  } yield StageProfile(profileName, profileVersion, overlays)

  val activateProfiles = for {
    profileName <- arbitrary[String]
    profileVersion <- arbitrary[String]
    overlays <- arbitrary[List[OverlayRef]]
  } yield ActivateProfile(profileName, profileVersion, overlays)

  val updateActions: Gen[UpdateAction] = Gen.oneOf(
    addRuntimeConfigs,
    addOverlayConfigs,
    stageProfiles,
    activateProfiles
  )
  implicit val arbUpdateAction: Arbitrary[UpdateAction] = Arbitrary(updateActions)

  val overlayStates = Gen.oneOf[OverlayState](OverlayState.Active, OverlayState.Valid, OverlayState.Invalid, OverlayState.Pending)
  implicit val arbOverlayState: Arbitrary[OverlayState] = Arbitrary(overlayStates)

  val overlaySets = for {
    overlays <- arbitrary[List[OverlayRef]]
    state <- arbitrary[OverlayState]
    reason <- arbitrary[Option[String]]
  } yield OverlaySet(overlays, state, reason)
  implicit val arbOverlaySets: Arbitrary[OverlaySet] = Arbitrary(overlaySets)

  val serviceInfos = for {
    name <- arbitrary[String]
    serviceType <- arbitrary[String]
    timestamp <- arbitrary[ju.Date]
    lifetimeMsec <- Gen.choose(0, Long.MaxValue) // arbitrary[Long].filter(_ >= 0)
    props <- arbitrary[Map[String, String]]
  } yield ServiceInfo(name, serviceType, timestamp.getTime(), lifetimeMsec, props)
  implicit val arbServiceInfo: Arbitrary[ServiceInfo] = Arbitrary(serviceInfos)

  val profiles = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    overlays <- arbitrary[List[OverlaySet]] if overlays.nonEmpty
  } yield Profile(name, version, overlays)
  implicit val arbProfile: Arbitrary[Profile] = Arbitrary(profiles)

  val singleProfiles = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    overlaySet <- arbitrary[OverlaySet]
  } yield SingleProfile(name, version, overlaySet)
  implicit val arbSingleProfile: Arbitrary[SingleProfile] = Arbitrary(singleProfiles)

  val containerInfos = for {
    containerId <- arbitrary[String]
    properties <- arbitrary[Map[String, String]]
    serviceInfos <- arbitrary[List[ServiceInfo]]
    profiles <- arbitrary[List[Profile]]
    timestamp <- arbitrary[ju.Date]
  } yield ContainerInfo(containerId, properties, serviceInfos, profiles, timestamp.getTime())
  implicit val arbContainerInfo: Arbitrary[ContainerInfo] = Arbitrary(containerInfos)

  val remoteContainerStates = for {
    containerInfo <- arbitrary[ContainerInfo]
    outstandingUpdateActions <- arbitrary[List[UpdateAction]]
  } yield RemoteContainerState(containerInfo, outstandingUpdateActions)
  implicit val arbRemoteContainerState: Arbitrary[RemoteContainerState] = Arbitrary(remoteContainerStates)

  val rolloutProfiles = for {
    profileName <- arbitrary[String]
    profileVersion <- arbitrary[String]
    overlays <- arbitrary[List[OverlayRef]]
    containerIds <- arbitrary[List[String]]
  } yield RolloutProfile(profileName, profileVersion, overlays, containerIds)
  implicit val arbRolloutProfile: Arbitrary[RolloutProfile] = Arbitrary(rolloutProfiles)

}

object TestData extends TestData
