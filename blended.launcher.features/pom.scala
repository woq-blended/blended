import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

val features: Map[String, Seq[FeatureBundle]] = Map(
   "blended-base-felix" -> Seq(
     FeatureBundle(dependency = felixFramework, startLevel = 0, start = true)
   ),
   "blended-base-equinox" -> Seq(
     FeatureBundle(dependency = eclipseOsgi, startLevel = 0, start = true),
     FeatureBundle(dependency = eclipseEquinoxConsole, start = true)
   ),
  "blended-base" -> Seq(
    // FeatureBundle(dependency = felixFramework, startLevel = 0, start = true),
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
    FeatureBundle(dependency = blendedContainerContext, start = true),
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
  ),
  "blended-activemq" -> Seq(
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
  ),
  "blended-camel" -> Seq(
    FeatureBundle(dependency = camelCore),
    FeatureBundle(dependency = camelSpring),
    FeatureBundle(dependency = camelJms),
    FeatureBundle(dependency = blendedCamelUtils),
    FeatureBundle(dependency = blendedJmsSampler, start = true)
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
    FeatureBundle(dependency = Dependency(gav = blendedMgmtRest, `type` = "war"), start = true),
    FeatureBundle(dependency = blendedMgmtRepo, start = true),
    FeatureBundle(dependency = Dependency(gav = blendedMgmtRepoRest, `type` = "war"), start = true),
    FeatureBundle(dependency = blendedUpdaterRemote, start = true),
    FeatureBundle(dependency = blendedContainerRegistry),
    FeatureBundle(dependency = blendedPersistence),
    FeatureBundle(dependency = blendedPersistenceOrient, start = true),
    FeatureBundle(dependency = orientDbCore),
    FeatureBundle(dependency = concurrentLinkedHashMapLru),
    FeatureBundle(dependency = jsr305),
    FeatureBundle(dependency = Dependency(gav = blendedMgmtUi, `type` = "war"), start = true),
    FeatureBundle(dependency = jacksonAnnotations),
    FeatureBundle(dependency = jacksonCore),
    FeatureBundle(dependency = jacksonBind),
    FeatureBundle(dependency = jjwt),
    FeatureBundle(dependency = blendedSecurityLogin, start = true),
    FeatureBundle(dependency = Dependency(gav = blendedSecurityLoginRest, `type` = "war"), start = true)
  ),
  "blended-jetty" -> Seq(
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
    FeatureBundle(dependency = jettyOsgiBoot, start = true),
    FeatureBundle(dependency = jettyHttpService, start = true)
  ),
  "blended-security" -> Seq(
    FeatureBundle(dependency = shiroCore),
    FeatureBundle(dependency = shiroWeb),
    FeatureBundle(dependency = blendedSecurity, start = true)
  ),
  "blended-ssl" -> Seq(
    FeatureBundle(dependency = blendedSecurityCert, start = true)
  ),
  "blended-spray" -> Seq(
    FeatureBundle(dependency = javaxServlet31),
    FeatureBundle(dependency = blendedSprayApi),
    FeatureBundle(dependency = blendedSpray),
    FeatureBundle(dependency = blendedSecuritySpray)
  ),
  "blended-akka-http" -> Seq(
    FeatureBundle(dependency = blendedAkkaHttp, start = true),
    FeatureBundle(dependency = Deps.akkaHttp),
    FeatureBundle(dependency = Deps.akkaHttpCore),
    FeatureBundle(dependency = Deps.akkaParsing)
  ),
  "blended-spring" -> Seq(
    FeatureBundle(dependency = aopAlliance),
    FeatureBundle(dependency = springCore),
    FeatureBundle(dependency = springExpression),
    FeatureBundle(dependency = springBeans),
    FeatureBundle(dependency = springAop),
    FeatureBundle(dependency = springContext),
    FeatureBundle(dependency = springContextSupport),
    FeatureBundle(dependency = springTx)
  ),
  "blended-samples" -> Seq(
    FeatureBundle(dependency = blendedActivemqDefaultbroker, start = true),
    FeatureBundle(dependency = blendedActivemqClient, start = true),
    FeatureBundle(dependency = Dependency(gav = blendedSamplesSprayHelloworld, `type` = "war"), start = true),
    FeatureBundle(dependency = blendedSamplesCamel, start = true),
    FeatureBundle(dependency = blendedSamplesJms, start = true),
    FeatureBundle(dependency = blendedFile),
    FeatureBundle(dependency = blendedAkkaHttpSampleHelloworld, start = true)
  )
)

BlendedModel(
  blendedLauncherFeatures,
  packaging = "jar",
  description = "The prepackaged features for blended.",
  dependencies = featureDependencies(features),
  plugins = featuresMavenPlugins(features)
)
