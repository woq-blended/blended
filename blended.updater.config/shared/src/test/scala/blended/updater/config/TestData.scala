package blended.updater.config

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

trait TestData {

  val artifacts = for {
    fileName <- arbitrary[String]
    sha1Sum <- arbitrary[Option[String]]
  } yield
    Artifact(
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
    url <- arbitrary[String]
    names <- arbitrary[List[String]]
  } yield FeatureRef(url, names)
  implicit val arbFeatureRefs: Arbitrary[FeatureRef] = Arbitrary(featureRefs)

  val featurConfigs = for {
    repoUrl <- arbitrary[String]
    name <- arbitrary[String]
    bundles <- arbitrary[List[BundleConfig]]
    features <- arbitrary[List[FeatureRef]]
  } yield FeatureConfig(repoUrl, name, bundles, features)
  implicit val arbFeatureConfig: Arbitrary[FeatureConfig] = Arbitrary(featurConfigs)

  val generatedConfigs = for {
    configFile <- arbitrary[String]
    config <- arbitrary[String]
  } yield GeneratedConfig(configFile, config)
  implicit val arbGeneratedConfig: Arbitrary[GeneratedConfig] = Arbitrary(generatedConfigs)

  val profiles = for {
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
  } yield
    Profile(
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
  implicit val arbProfile: Arbitrary[Profile] = Arbitrary(profiles)

  val overlayStates =
    Gen.oneOf[OverlayState](OverlayState.Active, OverlayState.Valid, OverlayState.Invalid, OverlayState.Pending)
  implicit val arbOverlayState: Arbitrary[OverlayState] = Arbitrary(overlayStates)

  val serviceInfos = for {
    name <- arbitrary[String]
    serviceType <- arbitrary[String]
    timestampMsec <- Gen.choose(10000, Long.MaxValue)
    lifetimeMsec <- Gen.choose(0, Long.MaxValue)
    props <- arbitrary[Map[String, String]]
  } yield ServiceInfo(name, serviceType, timestampMsec, lifetimeMsec, props)
  implicit val arbServiceInfo: Arbitrary[ServiceInfo] = Arbitrary(serviceInfos)

  val profileRefs = for {
    name <- arbitrary[String]
    version <- arbitrary[String]
  } yield ProfileRef(name, version)
  implicit val arbProfileRef: Arbitrary[ProfileRef] = Arbitrary(profileRefs)

  val containerInfos = for {
    containerId <- arbitrary[String]
    properties <- arbitrary[Map[String, String]]
    serviceInfos <- arbitrary[List[ServiceInfo]]
    profiles <- arbitrary[List[ProfileRef]]
    timestampMsec <- Gen.choose(10000, Long.MaxValue)
  } yield ContainerInfo(containerId, properties, serviceInfos, profiles, timestampMsec)
  implicit val arbContainerInfo: Arbitrary[ContainerInfo] = Arbitrary(containerInfos)

  val remoteContainerStates = for {
    containerInfo <- arbitrary[ContainerInfo]
  } yield RemoteContainerState(containerInfo)
  implicit val arbRemoteContainerState: Arbitrary[RemoteContainerState] = Arbitrary(remoteContainerStates)

  val rolloutProfiles = for {
    profileName <- arbitrary[String]
    profileVersion <- arbitrary[String]
    containerIds <- arbitrary[List[String]]
  } yield RolloutProfile(profileName, profileVersion, containerIds)
  implicit val arbRolloutProfile: Arbitrary[RolloutProfile] = Arbitrary(rolloutProfiles)

}

object TestData extends TestData
