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
  implicit def arbArtifact: Arbitrary[Artifact] = Arbitrary(artifacts)

  val bundleConfigs = for {
    artifact <- arbitrary[Artifact]
    start <- arbitrary[Boolean]
    startLevel <- arbitrary[Option[Int]]
  } yield BundleConfig(artifact, start, startLevel)

  implicit def arbBundleConfig: Arbitrary[BundleConfig] = Arbitrary(bundleConfigs)

  val featureRefs = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    url <- arbitrary[Option[String]]
  } yield FeatureRef(name, version, url)
  implicit def arbFeatureRefs = Arbitrary(featureRefs)

  val featurConfigs = for {
    featureRef <- arbitrary[FeatureRef]
    bundles <- arbitrary[List[BundleConfig]]
    features <- arbitrary[List[FeatureRef]]
  } yield FeatureConfig(featureRef.name, featureRef.version, featureRef.url, bundles, features)
  implicit def arbFeatureConfig = Arbitrary(featurConfigs)

  val generatedConfigs = for {
    configFile <- arbitrary[String]
    config <- arbitrary[String]
  } yield GeneratedConfig(configFile, config)
  implicit def arbGeneratedConfig: Arbitrary[GeneratedConfig] = Arbitrary(generatedConfigs)

  val overlayRefs = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
  } yield OverlayRef(name, version)
  implicit def arbOverlayRef: Arbitrary[OverlayRef] = Arbitrary(overlayRefs)

  val overlayConfigs = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    generatedConfigs <- arbitrary[List[GeneratedConfig]]
    properties <- arbitrary[Map[String, String]]
  } yield OverlayConfig(name, version, generatedConfigs, properties)
  implicit def arbOverlayConfig = Arbitrary(overlayConfigs)

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
  implicit def arbRuntimeConfigs: Arbitrary[RuntimeConfig] = Arbitrary(runtimeConfigs)

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
  implicit def arbUpdateAction: Arbitrary[UpdateAction] = Arbitrary(updateActions)

  val overlayStates = Gen.oneOf[OverlayState](OverlayState.Active, OverlayState.Valid, OverlayState.Invalid, OverlayState.Pending)
  implicit def arbOverlayState: Arbitrary[OverlayState] = Arbitrary(overlayStates)

  val overlaySets = for {
    overlays <- arbitrary[List[OverlayRef]]
    state <- arbitrary[OverlayState]
    reason <- arbitrary[Option[String]]
  } yield OverlaySet(overlays, state, reason)
  implicit def arbOverlaySets: Arbitrary[OverlaySet] = Arbitrary(overlaySets)

  val serviceInfos = for {
    name <- arbitrary[String]
    serviceType <- arbitrary[String]
    timestamp <- arbitrary[ju.Date]
    lifetimeMsec <- arbitrary[Long].filter(_ >= 0)
    props <- arbitrary[Map[String, String]]
  } yield ServiceInfo(name, serviceType, timestamp.getTime(), lifetimeMsec, props)
  implicit def arbServiceInfo: Arbitrary[ServiceInfo] = Arbitrary(serviceInfos)

  val profiles = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
    overlays <- arbitrary[List[OverlaySet]] if !overlays.isEmpty
  } yield Profile(name, version, overlays)
  implicit def arbProfile: Arbitrary[Profile] = Arbitrary(profiles)

  val containerInfos = for {
    containerId <- arbitrary[String]
    properties <- arbitrary[Map[String, String]]
    serviceInfos <- arbitrary[List[ServiceInfo]]
    profiles <- arbitrary[List[Profile]]
    timestamp <- arbitrary[ju.Date]
  } yield ContainerInfo(containerId, properties, serviceInfos, profiles, timestamp.getTime())
  implicit def arbContainerInfo: Arbitrary[ContainerInfo] = Arbitrary(containerInfos)

  val remoteContainerStates = for {
    containerInfo <- arbitrary[ContainerInfo]
    outstandingUpdateActions <- arbitrary[List[UpdateAction]]
  } yield RemoteContainerState(containerInfo, outstandingUpdateActions)
  implicit def arbRemoteContainerState: Arbitrary[RemoteContainerState] = Arbitrary(remoteContainerStates)

}

object TestData extends TestData
