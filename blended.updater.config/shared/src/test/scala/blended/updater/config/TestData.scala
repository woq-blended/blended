package blended.updater.config

import org.scalacheck.Gen

trait TestData {

  val genSize : Int = 20

  val testString : Gen[String] = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)

  val stringPairs : Gen[(String, String)] = for {
    v1 <- testString
    v2 <- testString
  } yield (v1,v2)

  val artifacts : Gen[Artifact] = for {
    fileName <- testString
    sha1Sum <- Gen.option(testString)
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
    url <- testString
    names <- Gen.listOfN(genSize, testString)
  } yield FeatureRef(url, names)

  val featureConfigs : Gen[FeatureConfig] = for {
    repoUrl <- testString
    name <- testString
    bundles <- Gen.listOf(bundleConfigs)
    features <- Gen.listOfN(genSize, featureRefs)
  } yield FeatureConfig(repoUrl, name, bundles, features)

  val generatedConfigs : Gen[GeneratedConfig] = for {
    configFile <- testString
    config <- testString
  } yield GeneratedConfig(configFile, config)

  val profiles : Gen[Profile] = for {
    name <- testString
    version <- testString
    bundles <- Gen.listOfN(genSize, bundleConfigs)
    startLevel <- Gen.posNum[Int]
    defaultStartLevel <- Gen.posNum[Int]
    properties <- Gen.mapOfN(genSize, stringPairs)
    frameworkProperties <- Gen.mapOfN(genSize, stringPairs)
    systemProperties <- Gen.mapOfN(genSize, stringPairs)
    features <- Gen.listOfN(genSize, featureRefs)
    resources <- Gen.listOfN(genSize, artifacts)
    resolvedFeatures <- Gen.listOfN(genSize, featureConfigs)
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

  val overlayStates : Gen[OverlayState] =
    Gen.oneOf[OverlayState](OverlayState.Active, OverlayState.Valid, OverlayState.Invalid, OverlayState.Pending)

  val serviceInfos : Gen[ServiceInfo] = for {
    name <- testString
    serviceType <- testString
    timestampMsec <- Gen.choose(10000, Long.MaxValue)
    lifetimeMsec <- Gen.choose(0, Long.MaxValue)
    props <- Gen.mapOfN(genSize, stringPairs)
  } yield ServiceInfo(name, serviceType, timestampMsec, lifetimeMsec, props)

  val profileRefs : Gen[ProfileRef] = for {
    name <- testString
    version <- testString
  } yield ProfileRef(name, version)

  val containerInfos : Gen[ContainerInfo] = for {
    containerId <- testString
    properties <- Gen.mapOfN(genSize, stringPairs)
    serviceInfos <- Gen.listOfN(genSize, serviceInfos)
    profiles <- Gen.listOfN(genSize, profileRefs)
    timestampMsec <- Gen.choose(10000, Long.MaxValue)
  } yield ContainerInfo(containerId, properties, serviceInfos, profiles, timestampMsec)

  val remoteContainerStates : Gen[RemoteContainerState] = for {
    containerInfo <- containerInfos
  } yield RemoteContainerState(containerInfo)

  val rolloutProfiles : Gen[RolloutProfile] = for {
    profileName <- testString
    profileVersion <- testString
    containerIds <- Gen.listOfN(genSize, testString)
  } yield RolloutProfile(profileName, profileVersion, containerIds)
}

object TestData extends TestData
