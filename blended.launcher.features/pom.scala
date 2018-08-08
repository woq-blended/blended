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
    FeatureBundle(dependency = Blended.securityBoot),
    FeatureBundle(dependency = asmAll, start = true),
    FeatureBundle(dependency = Blended.updater, start = true),
    FeatureBundle(dependency = Blended.updaterConfig, start = true),
    FeatureBundle(dependency = scalaReflect),
    FeatureBundle(dependency = scalaLib),
    FeatureBundle(dependency = scalaXml),
    FeatureBundle(dependency = scalaCompatJava8),
    FeatureBundle(dependency = scalaParser),
    FeatureBundle(dependency = Blended.akka, start = true),
    FeatureBundle(dependency = Blended.utilLogging),
    FeatureBundle(dependency = Blended.util, start = true),
    FeatureBundle(dependency = Blended.containerContextApi),
    FeatureBundle(dependency = Blended.containerContextImpl, start = true),
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
    FeatureBundle(dependency = Blended.domino),
    FeatureBundle(dependency = Blended.mgmtBase, start = true),
    FeatureBundle(dependency = Blended.prickle),
    FeatureBundle(dependency = Blended.mgmtServiceJmx, start = true)
  )),
  FeatureDef(
    "blended-activemq",
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
      FeatureBundle(dependency = Blended.activemqBrokerstarter),
      FeatureBundle(dependency = Blended.jmsUtils, start = true),
      FeatureBundle(dependency = springJms)
    )
  ),
  FeatureDef(
    "blended-camel",
    features = Seq(
      "blended-spring"
    ),
    bundles = Seq(
      FeatureBundle(dependency = geronimoJms11Spec),
      FeatureBundle(dependency = camelCore),
      FeatureBundle(dependency = camelSpring),
      FeatureBundle(dependency = camelJms),
      FeatureBundle(dependency = Blended.camelUtils),
      FeatureBundle(dependency = Blended.jmsSampler, start = true)
    )
  ),
  FeatureDef("blended-commons", bundles = Seq(
    FeatureBundle(dependency = ariesUtil),
    FeatureBundle(dependency = ariesJmxApi),
    FeatureBundle(dependency = ariesJmxCore, start = true),
    FeatureBundle(dependency = Blended.jmx, start = true),
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
  FeatureDef(
    "blended-hawtio",
    features = Seq(
      "blended-jetty"
    ),
    bundles = Seq(
      FeatureBundle(dependency = hawtioWeb, start = true),
      FeatureBundle(dependency = Blended.hawtioLogin)
    )
  ),
  FeatureDef("blended-mgmt-client", bundles = Seq(
    FeatureBundle(dependency = Blended.mgmtAgent, start = true)
  )),
  FeatureDef(
    "blended-mgmt-server",
    features = Seq(
      "blended-base",
      "blended-akka-http",
      "blended-security",
      "blended-ssl"
    ),
    bundles = Seq(
      FeatureBundle(dependency = Blended.mgmtRest, start = true),
      FeatureBundle(dependency = Blended.mgmtRepo, start = true),
      FeatureBundle(dependency = Blended.mgmtRepoRest, start = true),
      FeatureBundle(dependency = Blended.updaterRemote, start = true),
      FeatureBundle(dependency = Blended.persistence),
      FeatureBundle(dependency = Blended.persistenceOrient, start = true),
      FeatureBundle(dependency = orientDbCore),
      FeatureBundle(dependency = concurrentLinkedHashMapLru),
      FeatureBundle(dependency = jsr305),
      FeatureBundle(dependency = jacksonAnnotations),
      FeatureBundle(dependency = jacksonCore),
      FeatureBundle(dependency = jacksonBind),
      FeatureBundle(dependency = jjwt),
      FeatureBundle(dependency = Blended.securityLogin, start = true),
      FeatureBundle(dependency = Blended.mgmtWs, start = true),
      FeatureBundle(dependency = Blended.securityLoginRest, start = true)
      //FeatureBundle(dependency = Dependency(gav = Blended.mgmtApp, `type` = "war"), start = true)
    )
  ),
  FeatureDef(
    "blended-jetty",
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
      FeatureBundle(dependency = Blended.jettyBoot, start = true),
      FeatureBundle(dependency = jettyHttpService, start = true)
    )
  ),
  FeatureDef(
    "blended-security",
    features = Seq(
      "blended-base"
    ),
    bundles = Seq(
      FeatureBundle(dependency = Blended.security, start = true)
    )
  ),
  FeatureDef(
    "blended-ssl",
    features = Seq(
      "blended-base"
    ),
    bundles = Seq(
      FeatureBundle(dependency = Blended.securityScep, start = true),
      FeatureBundle(dependency = Blended.securitySsl, start = true)
    )
  ),
  FeatureDef(
    "blended-akka-http",
    features = Seq(
      "blended-base"
    ),
    bundles = Seq(
      FeatureBundle(dependency = Blended.akkaHttpApi),
      FeatureBundle(dependency = Blended.akkaHttp, start = true),
      FeatureBundle(dependency = Blended.prickleAkkaHttp),
      FeatureBundle(dependency = Blended.securityAkkaHttp)
    )
  ),
  FeatureDef(
    "blended-akka-http-modules",
    features = Seq(
      "blended-spring",
      "blended-camel",
      "blended-akka-http"
    ),
    bundles = Seq(
      FeatureBundle(dependency = Blended.akkaHttpProxy),
      FeatureBundle(dependency = Blended.akkaHttpRestJms),
      FeatureBundle(dependency = Blended.akkaHttpJmsQueue)
    )
  ),
  FeatureDef("blended-spring", bundles = Seq(
    FeatureBundle(dependency = aopAlliance),
    FeatureBundle(dependency = springCore),
    FeatureBundle(dependency = springExpression),
    FeatureBundle(dependency = springBeans),
    FeatureBundle(dependency = springAop),
    FeatureBundle(dependency = springContext),
    FeatureBundle(dependency = springContextSupport),
    FeatureBundle(dependency = springTx),
    FeatureBundle(dependency = springJms)
  )),
  FeatureDef(
    "blended-samples",
    features = Seq(
      "blended-akka-http",
      "blended-activemq",
      "blended-camel"
    ),
    bundles = Seq(
      FeatureBundle(dependency = Blended.activemqDefaultbroker, start = true),
      FeatureBundle(dependency = Blended.activemqClient, start = true),
      FeatureBundle(dependency = Blended.samplesCamel, start = true),
      FeatureBundle(dependency = Blended.samplesJms, start = true),
      FeatureBundle(dependency = Blended.file),
      FeatureBundle(dependency = Blended.akkaHttpSampleHelloworld, start = true)
    )
  )
)

BlendedModel(
  gav = Blended.launcherFeatures,
  packaging = "jar",
  description = "The prepackaged features for blended.",
  dependencies = featureDependencies(features).map(_.intransitive),
  plugins = featuresMavenPlugins(features)
)
