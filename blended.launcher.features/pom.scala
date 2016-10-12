import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

val features : Map[String, Seq[FeatureBundle]] = Map(
  "blended-base" -> Seq(
    FeatureBundle(dependency = felixFramework, startLevel = 0, start = true),
    FeatureBundle(dependency = blendedSecurityBoot),
    FeatureBundle(dependency = asmAll, start = true),
    FeatureBundle(dependency = blendedUpdater, start = true),
    FeatureBundle(dependency = blendedUpdaterConfig, start = true),
    FeatureBundle(dependency = scalaReflect),
    FeatureBundle(dependency = scalaLib),
    FeatureBundle(dependency = scalaXml),
    FeatureBundle(dependency = blendedAkka, start=true),
    FeatureBundle(dependency = blendedUtil, start=true),
    FeatureBundle(dependency = blendedContainerContext, start=true),
    FeatureBundle(dependency = felixConfigAdmin, start=true),
    FeatureBundle(dependency = felixEventAdmin, start=true),
    FeatureBundle(dependency = felixFileinstall, start=true),
    FeatureBundle(dependency = blendedUtil, start=true),
    FeatureBundle(dependency = slf4jJcl),
    FeatureBundle(dependency = slf4jJul),
    FeatureBundle(dependency = slf4j),
    FeatureBundle(dependency = logbackCore),
    FeatureBundle(dependency = logbackClassic),
    FeatureBundle(dependency = felixGogoRuntime, start=true),
    FeatureBundle(dependency = felixGogoShell, start=true),
    FeatureBundle(dependency = felixGogoCommand, start=true),
    FeatureBundle(dependency = felixMetatype, start=true),
    FeatureBundle(dependency = typesafeConfig),
    FeatureBundle(dependency = akkaActor),
    FeatureBundle(dependency = akkaOsgi),
    FeatureBundle(dependency = akkaSlf4j),
    FeatureBundle(dependency = domino),
    FeatureBundle(dependency = blendedDomino),
    FeatureBundle(dependency = blendedMgmtBase, start = true)
  ),
  "blended-activemq" -> Seq(
    FeatureBundle(dependency = ariesProxyApi),
    FeatureBundle(dependency = ariesBlueprintApi),
    FeatureBundle(dependency = ariesBlueprintCore),
    FeatureBundle(dependency = geronimoAnnotation),
    FeatureBundle(dependency = geronimoJms11Spec),
    FeatureBundle(dependency = geronimoJ2eeMgmtSpec),
    FeatureBundle(dependency = servicemixJaxbApi),
    FeatureBundle(dependency = servicemixStaxApi),
    FeatureBundle(dependency = servicemixJaxbRuntime),
    FeatureBundle(dependency = servicemixJaxbImpl),
    FeatureBundle(dependency = xbeanSpring),
    FeatureBundle(dependency = activeMqOsgi),
    FeatureBundle(dependency = blendedActivemqBrokerstarter)
  ),
  "blended-camel" -> Seq(
    FeatureBundle(dependency = camelCore),
    FeatureBundle(dependency = camelSpring),
    FeatureBundle(dependency = camelJms),
    FeatureBundle(dependency = camelHttp),
    FeatureBundle(dependency = camelHttpCommon),
    FeatureBundle(dependency = camelServlet),
    FeatureBundle(dependency = camelServletListener),
    FeatureBundle(dependency = blendedCamelUtils),
    FeatureBundle(dependency = blendedJmsUtils, start = true)
  ),
  "blended-commons" -> Seq(
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
    FeatureBundle(dependency = commonsBeanUtils)
  ),
  "blended-hawtio" -> Seq(
    FeatureBundle(dependency = hawtioWeb, start = true),
    FeatureBundle(dependency = blendedHawtioLogin)
  ),
  "blended-mgmt-client" -> Seq(
    FeatureBundle(dependency = blendedMgmtAgent, start = true)
  ),
  "blended-mgmt-server" -> Seq(
    FeatureBundle(dependency = blendedMgmtRest, start = true),
    FeatureBundle(dependency = blendedMgmtRepo, start = true),
    FeatureBundle(dependency = blendedMgmtRepoRest, start = true),
    FeatureBundle(dependency = blendedUpdaterRemote, start = true),
    FeatureBundle(dependency = blendedContainerRegistry),
    FeatureBundle(dependency = blendedPersistence),
    FeatureBundle(dependency = blendedPersistenceOrient),
    FeatureBundle(dependency = orientDbCore),
    FeatureBundle(dependency = concurrentLinkedHashMapLru),
    FeatureBundle(dependency = jsr305),
    FeatureBundle(dependency = Dependency(gav = blendedMgmtUi, `type` = "war"), start=true)
  ),
  "blended-http" -> Seq(
    FeatureBundle(dependency = ops4jBaseLang),
    FeatureBundle(dependency = paxSwissboxCore),
    FeatureBundle(dependency = paxSwissboxOptJcl),
    FeatureBundle(dependency = xbeanBundleUtils),
    FeatureBundle(dependency = xbeanAsmShaded),
    FeatureBundle(dependency = xbeanReflect),
    FeatureBundle(dependency = xbeanFinder),
    FeatureBundle(dependency = paxwebApi),
    FeatureBundle(dependency = paxwebSpi),
    FeatureBundle(dependency = paxwebRuntime, start = true),
    FeatureBundle(dependency = paxwebJetty, start = true),
    FeatureBundle(dependency = paxwebJsp),
    FeatureBundle(dependency = paxwebExtWhiteboard, start = true),
    FeatureBundle(dependency = paxwebExtWar, start = true)
  ),
  "blended-jetty" -> Seq(
    FeatureBundle(dependency = activationApi),
    FeatureBundle(dependency = geronimoServlet30Spec),
    FeatureBundle(dependency = javaxMail),
    FeatureBundle(dependency = geronimoAnnotation),
    FeatureBundle(dependency = geronimoJaspic),
    FeatureBundle(dependency = jettyServer, start = true)
  ),
  "blended-jaxrs" -> Seq(
    FeatureBundle(dependency = jettison),
    FeatureBundle(dependency = jacksonCoreAsl),
    FeatureBundle(dependency = jacksonMapperAsl),
    FeatureBundle(dependency = jacksonJaxrs),
    FeatureBundle(dependency = jerseyCore),
    FeatureBundle(dependency = jerseyJson),
    FeatureBundle(dependency = jerseyServer),
    FeatureBundle(dependency = jerseyServlet),
    FeatureBundle(dependency = jerseyClient),
    FeatureBundle(dependency = geronimoServlet30Spec)
  ),
  "blended-security" -> Seq(
    FeatureBundle(dependency = shiroCore),
    FeatureBundle(dependency = shiroWeb),
    FeatureBundle(dependency = blendedSecurity, start = true)
  ),
  "blended-spray" -> Seq(
    FeatureBundle(dependency = blendedSprayApi),
    FeatureBundle(dependency = blendedSpray)
  ),
  "blended-spring" -> Seq(
    FeatureBundle(dependency = aopAlliance),
    FeatureBundle(dependency = springCore),
    FeatureBundle(dependency = springExpression),
    FeatureBundle(dependency = springBeans),
    FeatureBundle(dependency = springAop),
    FeatureBundle(dependency = springContext),
    FeatureBundle(dependency = springContextSupport),
    FeatureBundle(dependency = springTx),
    FeatureBundle(dependency = springJms)
  ),
  "blended-samples" -> Seq(
      FeatureBundle(dependency = blendedActivemqDefaultbroker, start = true),
      FeatureBundle(dependency = blendedActivemqClient, start = true),
      FeatureBundle(dependency = blendedSamplesSprayHelloworld, start = true),
      FeatureBundle(dependency = blendedSamplesCamel, start = true),
      FeatureBundle(dependency = blendedSamplesJms, start = true)
  )
)

BlendedModel(
  blendedLauncherFeatures,
  packaging = "jar",
  prerequisites = Prerequisites(
    maven = "3.3.3"
  ),
  dependencies = featureDependencies(features),
  plugins = featuresMavenPlugins(features)
)
