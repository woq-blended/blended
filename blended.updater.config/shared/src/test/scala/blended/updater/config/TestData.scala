package blended.updater.config

import org.scalacheck.Gen

trait TestData {

  val stringPairs = for {
    v1 <- Gen.alphaNumStr
    v2 <- Gen.alphaNumStr
  } yield (v1,v2)

  val artifacts : Gen[Artifact] = for {
    fileName <- Gen.alphaNumStr
    sha1Sum <- Gen.option(Gen.alphaNumStr)
  } yield
    Artifact(
      url = s"http://fakeurl/${fileName}",
      fileName = Gen.oneOf(Some(fileName), None).sample.flatten,
      sha1Sum = sha1Sum
    )

  val bundleConfigs : Gen[BundleConfig] = for {
    artifact <- artifacts
    start <- Gen.oneOf(true, false)
    startLevel <- Gen.option(Gen.posNum[Int])
  } yield BundleConfig(artifact, start, startLevel)

  val featureRefs : Gen[FeatureRef] = for {
    url <- Gen.alphaNumStr
    names <- Gen.listOf(Gen.alphaNumStr)
  } yield FeatureRef(url, names)

  val featurConfigs = for {
    repoUrl <- Gen.alphaNumStr.suchThat(s => s.nonEmpty && s.size <= 100)
    name <- Gen.alphaNumStr
    bundles <- Gen.listOf(bundleConfigs)
    features <- Gen.listOf(featureRefs)
  } yield FeatureConfig(repoUrl, name, bundles, features)

  val generatedConfigs = for {
    configFile <- Gen.alphaNumStr
    config <- Gen.alphaNumStr
  } yield GeneratedConfig(configFile, config)

  val profiles = for {
    name <- Gen.alphaNumStr
    version <- Gen.alphaNumStr
    bundles <- Gen.listOf(bundleConfigs)
    startLevel <- Gen.posNum[Int]
    defaultStartLevel <- Gen.posNum[Int]
    properties <- Gen.mapOf(stringPairs)
    frameworkProperties <- Gen.mapOf(stringPairs)
    systemProperties <- Gen.mapOf(stringPairs)
    features <- Gen.listOf(featureRefs)
    resources <- Gen.listOf(artifacts)
    resolvedFeatures <- Gen.listOf(featurConfigs)
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

  val overlayStates =
    Gen.oneOf[OverlayState](OverlayState.Active, OverlayState.Valid, OverlayState.Invalid, OverlayState.Pending)

  val serviceInfos = for {
    name <- Gen.alphaNumStr
    serviceType <- Gen.alphaNumStr
    timestampMsec <- Gen.choose(10000, Long.MaxValue)
    lifetimeMsec <- Gen.choose(0, Long.MaxValue)
    props <- Gen.mapOf(stringPairs)
  } yield ServiceInfo(name, serviceType, timestampMsec, lifetimeMsec, props)

  val profileRefs = for {
    name <- Gen.alphaNumStr
    version <- Gen.alphaNumStr
  } yield ProfileRef(name, version)

  val containerInfos = for {
    containerId <- Gen.alphaNumStr
    properties <- Gen.mapOf(stringPairs)
    serviceInfos <- Gen.listOf(serviceInfos)
    profiles <- Gen.listOf(profileRefs)
    timestampMsec <- Gen.choose(10000, Long.MaxValue)
  } yield ContainerInfo(containerId, properties, serviceInfos, profiles, timestampMsec)

  val remoteContainerStates = for {
    containerInfo <- containerInfos
  } yield RemoteContainerState(containerInfo)

  val rolloutProfiles = for {
    profileName <- Gen.alphaNumStr
    profileVersion <- Gen.alphaNumStr
    containerIds <- Gen.listOf(Gen.alphaNumStr)
  } yield RolloutProfile(profileName, profileVersion, containerIds)
}

object TestData extends TestData
