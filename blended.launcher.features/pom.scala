import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

val features = Seq(
  FeatureDef("blended-base-felix", bundles = Seq(
    FeatureBundle(dependency = felixFramework, startLevel = 0, start = true)
  )),
  FeatureDef("blended-base-equinox", bundles = Seq(
    FeatureBundle(dependency = eclipseOsgi, startLevel = 0, start = true),
    FeatureBundle(dependency = eclipseEquinoxConsole, start = true)
  )),
  FeatureDef("blended-base", bundles = Seq(
    FeatureBundle(dependency = blendedSecurityBoot),
    FeatureBundle(dependency = asmAll, start = true),
    FeatureBundle(dependency = blendedUpdater, start = true),
    FeatureBundle(dependency = blendedUpdaterConfig, start = true),
    FeatureBundle(dependency = scalaReflect),
    FeatureBundle(dependency = scalaLib),
    FeatureBundle(dependency = scalaXml),
    FeatureBundle(dependency = scalaCompatJava8),
    FeatureBundle(dependency = scalaParser),
    FeatureBundle(dependency = blendedAkka, start = true),
    FeatureBundle(dependency = blendedUtil, start = true),
    FeatureBundle(dependency = blendedContainerContextApi),
    FeatureBundle(dependency = blendedContainerContextImpl, start = true),
    FeatureBundle(dependency = felixConfigAdmin, start = true),
    FeatureBundle(dependency = felixEventAdmin, start = true),
    FeatureBundle(dependency = felixFileinstall, start = true),
    FeatureBundle(dependency = slf4jJcl),
    FeatureBundle(dependency = slf4jJul),
    FeatureBundle(dependency = slf4j),
    FeatureBundle(dependency = logbackCore),
    FeatureBundle(dependency = logbackClassic),
    FeatureBundle(dependency = felixGogoRuntime, start = true),
    FeatureBundle(dependency = felixGogoShell, start = true),
    FeatureBundle(dependency = felixGogoCommand, start = true),
    FeatureBundle(dependency = felixMetatype, start = true),
    FeatureBundle(dependency = typesafeConfig),
    FeatureBundle(dependency = typesafeConfigSSL),
    FeatureBundle(dependency = reactiveStreams),
    FeatureBundle(dependency = akkaActor),
    FeatureBundle(dependency = akkaOsgi),
    FeatureBundle(dependency = akkaSlf4j),
    FeatureBundle(dependency = akkaStream),
    FeatureBundle(dependency = domino),
    FeatureBundle(dependency = blendedDomino),
    FeatureBundle(dependency = blendedMgmtBase, start = true),
    FeatureBundle(dependency = blendedPrickle),
    FeatureBundle(dependency = blendedMgmtServiceJmx, start = true)
  )),
  FeatureDef("blended-activemq",
    bundles = Seq(
      FeatureBundle(dependency = ariesProxyApi),
      FeatureBundle(dependency = ariesBlueprintApi),
      FeatureBundle(dependency = ariesBlueprintCore),
      FeatureBundle(dependency = geronimoAnnotation),
      FeatureBundle(dependency = geronimoJms11Spec),
      FeatureBundle(dependency = geronimoJ2eeMgmtSpec),
      FeatureBundle(dependency = servicemixStaxApi),
      FeatureBundle(dependency = xbeanSpring),
      FeatureBundle(dependency = activeMqOsgi),
      FeatureBundle(dependency = blendedActivemqBrokerstarter),
      FeatureBundle(dependency = blendedJmsUtils, start = true),
      FeatureBundle(dependency = springJms)
    )),
  FeatureDef("blended-camel",
    features = Seq(
      "blended-spring"
    ),
    bundles = Seq(
      FeatureBundle(dependency = camelCore),
      FeatureBundle(dependency = camelSpring),
      FeatureBundle(dependency = camelJms),
      FeatureBundle(dependency = blendedCamelUtils),
      FeatureBundle(dependency = blendedJmsSampler, start = true)
    )),
  FeatureDef("blended-commons", bundles = Seq(
    FeatureBundle(dependency = ariesUtil),
    FeatureBundle(dependency = ariesJmxApi),
    FeatureBundle(dependency = ariesJmxCore, start = true),
    FeatureBundle(dependency = blendedJmx, start = true),
    FeatureBundle(dependency = commonsCollections),
    FeatureBundle(dependency = commonsDiscovery),
    FeatureBundle(dependency = commonsLang),
    FeatureBundle(dependency = commonsPool),
    FeatureBundle(dependency = commonsNet),
    FeatureBundle(dependency = commonsExec),
    FeatureBundle(dependency = commonsIo),
    FeatureBundle(dependency = commonsCodec),
    FeatureBundle(dependency = commonsHttpclient),
    FeatureBundle(dependency = commonsBeanUtils),
    FeatureBundle(dependency = commonsConfiguration2)
  )),
  FeatureDef("blended-hawtio",
    features = Seq(
      "blended-jetty"
    ),
    bundles = Seq(
      FeatureBundle(dependency = hawtioWeb, start = true),
      FeatureBundle(dependency = blendedHawtioLogin)
    )),
  FeatureDef("blended-mgmt-client", bundles = Seq(
    FeatureBundle(dependency = blendedMgmtAgent, start = true)
  )),
  FeatureDef("blended-mgmt-server",
    features = Seq(
      "blended-base",
      "blended-spray",
      "blended-security",
      "blended-ssl"
    ),
    bundles = Seq(
      FeatureBundle(dependency = blendedMgmtRest, start = true),
      FeatureBundle(dependency = blendedMgmtRepo, start = true),
      FeatureBundle(dependency = blendedMgmtRepoRest, start = true),
      FeatureBundle(dependency = blendedUpdaterRemote, start = true),
      FeatureBundle(dependency = blendedPersistence),
      FeatureBundle(dependency = blendedPersistenceOrient, start = true),
      FeatureBundle(dependency = orientDbCore),
      FeatureBundle(dependency = concurrentLinkedHashMapLru),
      FeatureBundle(dependency = jsr305),
      FeatureBundle(dependency = Dependency(gav = blendedMgmtApp, `type` = "war"), start = true),
      FeatureBundle(dependency = jacksonAnnotations),
      FeatureBundle(dependency = jacksonCore),
      FeatureBundle(dependency = jacksonBind),
      FeatureBundle(dependency = jjwt),
      FeatureBundle(dependency = blendedSecurityLogin, start = true),
      FeatureBundle(dependency = Dependency(gav = blendedSecurityLoginRest, `type` = "war"), start = true)
    )),
  FeatureDef("blended-jetty",
    features = Seq("blended-base"),
    bundles = Seq(
      FeatureBundle(dependency = activationApi),
      FeatureBundle(dependency = javaxServlet31),
      FeatureBundle(dependency = javaxMail),
      FeatureBundle(dependency = geronimoAnnotation),
      FeatureBundle(dependency = geronimoJaspic),
      FeatureBundle(dependency = jettyUtil),
      FeatureBundle(dependency = jettyHttp),
      FeatureBundle(dependency = jettyIo),
      FeatureBundle(dependency = jettyJmx),
      FeatureBundle(dependency = jettySecurity),
      FeatureBundle(dependency = jettyServlet),
      FeatureBundle(dependency = jettyServer),
      FeatureBundle(dependency = jettyWebapp),
      FeatureBundle(dependency = jettyDeploy),
      FeatureBundle(dependency = jettyXml),
      FeatureBundle(dependency = equinoxServlet),
      FeatureBundle(dependency = felixHttpApi),
      FeatureBundle(dependency = blendedJettyBoot, start = true),
      FeatureBundle(dependency = jettyHttpService, start = true)
    )),
  FeatureDef("blended-security",
    features = Seq(
      "blended-base"
    ),
    bundles = Seq(
      FeatureBundle(dependency = blendedSecurity, start = true)
    )),
  FeatureDef("blended-ssl",
    features = Seq(
      "blended-base"
    ),
    bundles = Seq(
      FeatureBundle(dependency = blendedSecurityScep, start = true),
      FeatureBundle(dependency = blendedSecuritySsl, start = true)
    )),
  FeatureDef("blended-spray",
    features = Seq(
      "blended-jetty"
    ),
    bundles = Seq(
      FeatureBundle(dependency = javaxServlet31),
      FeatureBundle(dependency = blendedSprayApi),
      FeatureBundle(dependency = blendedSpray)
    )),
  FeatureDef("blended-akka-http",
    features = Seq(
      "blended-base"
    ),
    bundles = Seq(
      FeatureBundle(dependency = blendedAkkaHttp, start = true),
      FeatureBundle(dependency = Deps.akkaHttp),
      FeatureBundle(dependency = Deps.akkaHttpCore),
      FeatureBundle(dependency = Deps.akkaParsing),
      FeatureBundle(dependency = blendedPrickleAkkaHttp),
      FeatureBundle(dependency = blendedSecurityAkkaHttp)
    )),
  FeatureDef("blended-spring", bundles = Seq(
    FeatureBundle(dependency = aopAlliance),
    FeatureBundle(dependency = springCore),
    FeatureBundle(dependency = springExpression),
    FeatureBundle(dependency = springBeans),
    FeatureBundle(dependency = springAop),
    FeatureBundle(dependency = springContext),
    FeatureBundle(dependency = springContextSupport),
    FeatureBundle(dependency = springTx)
  )),
  FeatureDef("blended-samples",
    features = Seq(
      "blended-akka-http",
      "blended-spray",
      "blended-activemq",
      "blended-camel"
    ),
    bundles = Seq(
      FeatureBundle(dependency = blendedActivemqDefaultbroker, start = true),
      FeatureBundle(dependency = blendedActivemqClient, start = true),
      FeatureBundle(dependency = Dependency(gav = blendedSamplesSprayHelloworld, `type` = "war"), start = true),
      FeatureBundle(dependency = blendedSamplesCamel, start = true),
      FeatureBundle(dependency = blendedSamplesJms, start = true),
      FeatureBundle(dependency = blendedFile),
      FeatureBundle(dependency = blendedAkkaHttpSampleHelloworld, start = true)
    ))
)

BlendedModel(
  blendedLauncherFeatures,
  packaging = "jar",
  description = "The prepackaged features for blended.",
  dependencies = featureDependencies(features),
  plugins = featuresMavenPlugins(features)
)
