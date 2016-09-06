// Polyglot Maven Scala file
// Shared build settings

import scala.collection.immutable

val blendedGroupId = "de.wayofquality.blended"
val blendedVersion = "2.0-SNAPSHOT"

implicit val scalaVersion = ScalaVersion("2.11.8")
val javaVersion = "1.7"

// We define the BlendedModel with some defaults, so that they can be reused
// throughout the build

object BlendedModel{

  // Properties we attach to all BlendedModels
  val defaultProperties : Map[String, String] =
    Map(
      "bundle.symbolicName" -> "${project.artifactId}",
      "bundle.namespace" -> "${project.artifactId}",
      "java.version" -> javaVersion,
      "scala.version" -> scalaVersion.binaryVersion,
      "blended.version" -> blendedVersion
    )

  // Profiles we attach to all BlendedModels
  val defaultProfiles = Seq()
//  Seq(Profile(
//    id = "gen-pom",
//    build = Build(
//      plugins = Seq(Plugin(
//        "io.takari.polyglot" % "polyglot-translate-plugin" % "0.1.19",
//        executions = Seq(
//          Execution(
//            id = "generate-pom.xml",
//            goals = Seq("translate"),
//            phase = "validate",
//            configuration = Config(
//              input = "pom.scala",
//              output = "pom.xml"
//            )
//          )
//        )
//      ))
//    )
//  ))

  val defaultDevelopers = Seq(
    Developer(
      email = "andreas@wayofquality.de",
      name = "Andreas Gies",
      organization = "WoQ - Way of Quality GmbH",
      organizationUrl = "http://www.wayofquality.de"
    ),
    Developer(
      email = "tobias.roeser@tototec.de",
      name = "Tobias Roeser",
      organization = "ToToTec"
    )
  )

  val defaultLicenses = Seq(
    License(
      name = "The Apache License, Version 2.0",
      url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
    )
  )

  val scm = Scm(
    connection = "scm:git:ssh://git@github.com/woq-blended/blended",
    developerConnection = "scm:git:ssh://git@github.com/woq-blended/blended.git",
    url = "https://github.com/woq-blended/blended"
  )

  val organization = Organization(
    name = "https://github.com/woq-blended",
    url = "https://github.com/woq-blended/blended"
  )

  val defaultRepositories = Seq(
    Repository(
      releases = RepositoryPolicy(
        enabled = true
      ),
      snapshots = RepositoryPolicy(
        enabled = false
      ),
      id = "FUSEStaging",
      url = "http://repo.fusesource.com/nexus/content/repositories/jboss-fuse-6.1.x"
    ),
    Repository(
      releases = RepositoryPolicy(
        enabled = true
      ),
      snapshots = RepositoryPolicy(
        enabled = false
      ),
      id = "SpringBundles",
      url = "http://repository.springsource.com/maven/bundles/release"
    ),
    Repository(
      releases = RepositoryPolicy(
        enabled = true
      ),
      snapshots = RepositoryPolicy(
        enabled = false
      ),
      id = "SpringExternalBundles",
      url = "http://repository.springsource.com/maven/bundles/external"
    )
  )

  val defaultResources = Seq(
      Resource(
        filtering = true,
        directory = "src/main/resources"
      ),
      Resource(
        directory = "src/main/binaryResources"
      )
    )

  val defaultTestResources = Seq(
      Resource(
        filtering = true,
        directory = "src/test/resources"
      ),
      Resource(
        directory = "src/test/binaryResources"
      )
    )

  val defaultPlugins = Seq(
    Plugin(
      "org.codehaus.mojo" % "build-helper-maven-plugin" % "1.9.1",
      executions = Seq(
        Execution(
          id = "add-scala-sources",
          phase = "generate-sources",
          goals = Seq(
            "add-source"
          ),
          configuration = Config(
            sources = Config(
              source = "src/main/scala"
            )
          )
        ),
        Execution(
          id = "add-scala-test-sources",
          phase = "generate-sources",
          goals = Seq(
            "add-test-source"
          ),
          configuration = Config(
            sources = Config(
              source = "src/test/scala"
            )
          )
        )
      )
    )
  )

  def apply(
    gav: Gav,
    build: Build = null,
    ciManagement: CiManagement = null,
    contributors: immutable.Seq[Contributor] = Nil,
    dependencyManagement: DependencyManagement = null,
    dependencies: immutable.Seq[Dependency] = Nil,
    description: String = null,
    developers: immutable.Seq[Developer] = Nil,
    distributionManagement: DistributionManagement = null,
    inceptionYear: String = null,
    issueManagement: IssueManagement = null,
    licenses: immutable.Seq[License] = Nil,
    mailingLists: immutable.Seq[MailingList] = Nil,
    modelEncoding: String = "UTF-8",
    modules: immutable.Seq[String] = Nil,
    packaging: String,
    pluginRepositories: immutable.Seq[Repository] = Nil,
    pomFile: Option[File] = None,
    prerequisites: Prerequisites = null,
    profiles: immutable.Seq[Profile] = Nil,
    properties: Map[String, String] = Map.empty,
    repositories: immutable.Seq[Repository] = Nil,
    parent: Parent = null,
    resources: Seq[Resource] = null,
    testResources: Seq[Resource] = null,
    plugins: Seq[Plugin] = null,
    pluginManagement: Seq[Plugin] = null

  ) = {
    if(parent != null) println(s"Project with parent: ${gav}")
    val theBuild = Option(build).orElse{
      val usedPlugins = Option(plugins).getOrElse(defaultPlugins)
      Option(Build(
          resources = Option(resources).getOrElse(defaultResources),
          testResources = Option(testResources).getOrElse(defaultTestResources),
          plugins = usedPlugins,
          pluginManagement = PluginManagement(plugins = Option(pluginManagement).getOrElse(usedPlugins))
        ))
      }

    new Model (
      gav = gav,
      build = theBuild,
      ciManagement = Option(ciManagement),
      contributors = contributors,
      dependencyManagement= Option(dependencyManagement).orElse(Option(DependencyManagement(dependencies))),
      dependencies = dependencies,
      description = Option(description),
      developers = defaultDevelopers ++ developers,
      distributionManagement = Option(distributionManagement),
      inceptionYear = Option(inceptionYear),
      issueManagement = Option(issueManagement),
      licenses = defaultLicenses ++ licenses,
      mailingLists = mailingLists,
      modelEncoding = modelEncoding,
      modelVersion = Some("4.0.0"),
      modules = modules,
      name = Some("${project.artifactId}"),
      organization = Option(organization),
      packaging = packaging,
      parent = Option(parent),
      pluginRepositories = pluginRepositories,
      pomFile = pomFile,
      prerequisites = Option(prerequisites),
      profiles = defaultProfiles ++ profiles,
      properties = defaultProperties ++ properties,
      repositories = defaultRepositories ++ repositories,
      scm = Option(scm),
      url = Some("https://github.com/woq-blended/blended")
    )
  }
}

// Blended Projects

def BlendedModule(name : String) = blendedGroupId % name % blendedVersion

val blendedParent = Parent(
  gav = BlendedModule("blended.parent"),
  relativePath = "../blended-parent"
)

val blendedActivemqBrokerstarter = BlendedModule("blended.activemq.brokerstarter")
val blendedActivemqClient = BlendedModule("blended.activemq.client")
val blendedAkka = BlendedModule("blended.akka")
val blendedCamelUtils = BlendedModule("blended.camel.utils")
val blendedContainerContext = BlendedModule("blended.container.context")
val blendedContainerRegistry = BlendedModule("blended.container.registry")
val blendedDemoLauncher = BlendedModule("blended.demo.launcher")
val blendedDemoMgmt = BlendedModule("blended.demo.mgmt")
val blendedDomino = BlendedModule("blended.domino")
val blendedHawtioLogin = BlendedModule("blended.hawtio.login")
val blendedItestSupport = BlendedModule("blended.itestsupport")
val blendedJmsUtils = BlendedModule("blended.jms.utils")
val blendedJmx = BlendedModule("blended.jmx")
val blendedJolokia = BlendedModule("blended.jolokia")
val blendedLauncher = BlendedModule("blended.launcher")
val blendedLauncherFeatures = BlendedModule("blended.launcher.features")
val blendedMgmtAgent = BlendedModule("blended.mgmt.agent")
val blendedMgmtBase = BlendedModule("blended.mgmt.base")
val blendedMgmtRepo = BlendedModule("blended.mgmt.repo")
val blendedMgmtRepoRest = BlendedModule("blended.mgmt.repo.rest")
val blendedMgmtMock = BlendedModule("blended.mgmt.mock")
val blendedMgmtRest = BlendedModule("blended.mgmt.rest")
val blendedMgmtUi = BlendedModule("blended.mgmt.ui")
val blendedPersistence = BlendedModule("blended.persistence")
val blendedPersistenceOrient = BlendedModule("blended.persistence.orient")
val blendedSecurity = BlendedModule("blended.security")
val blendedSecurityBoot = BlendedModule("blended.security.boot")
val blendedSpray = BlendedModule("blended.spray")
val blendedSprayApi = BlendedModule("blended.spray.api")
val blendedTestSupport = BlendedModule("blended.testsupport")
val blendedUpdater = BlendedModule("blended.updater")
val blendedUpdaterConfig = BlendedModule("blended.updater.config")
val blendedUpdaterRemote = BlendedModule("blended.updater.remote")
val blendedUpdaterMavenPlugin = BlendedModule("blended-updater-maven-plugin")
val blendedUtil = BlendedModule("blended.util")
