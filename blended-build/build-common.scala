// Polyglot Maven Scala file
// Shared build settings

import scala.collection.immutable

val blendedGroupId = "de.wayofquality.blended"
val blendedVersion = "2.0-SNAPSHOT"

implicit val scalaVersion = ScalaVersion("2.11.8")

val blendedParent = Parent(
  gav = blendedGroupId % "blended.parent" % blendedVersion,
  relativePath = "../blended-parent"
)

def BlendedModule(name : String) = blendedGroupId % name % blendedVersion

// We define the BlendedModel with some defaults, so that they can be reused
// throughout the build

object BlendedModel{

  // Properties we attach to all BlendedModels
  val defaultProperties : Map[String, String] =
    Map(
      "bundle.symbolicName" -> "${project.artifactId}",
      "bundle.namespace" -> "${project.artifactId}"
    )

  // Profiles we attach to all BlendedModels
  val defaultProfiles = Seq(Profile(
    id = "gen-pom",
    build = Build(
      plugins = Seq(Plugin(
        "io.takari.polyglot" % "polyglot-translate-plugin" % "0.1.15",
        executions = Seq(
          Execution(
            id = "generate-pom.xml",
            goals = Seq("translate"),
            phase = "validate",
            configuration = Config(
              input = "pom.scala",
              output = "pom.xml"
            )
          )
        )
      ))
    )
  ))

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
      organization = "ToToTec GbR"
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
    parent: Parent = null,
    pluginRepositories: immutable.Seq[Repository] = Nil,
    pomFile: Option[File] = None,
    prerequisites: Prerequisites = null,
    profiles: immutable.Seq[Profile] = Nil,
    properties: Map[String, String] = Map.empty,
    repositories: immutable.Seq[Repository] = Nil
  ) = new Model (
    gav = gav,
    build = Option(build),
    ciManagement = Option(ciManagement),
    contributors = contributors,
    dependencyManagement= Option(dependencyManagement),
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

// Blended Projects

val blendedAkka = BlendedModule("blended.akka")
val blendedContainerContext = BlendedModule("blended.container.context")
val blendedJmx = BlendedModule("blended.jmx")
val blendedLauncher = BlendedModule("blended.launcher")
val blendedMgmtBase = BlendedModule("blended.mgmt.base")
val blendedPersistence = BlendedModule("blended.persistence")
val blendedPersistenceOrient = BlendedModule("blended.persistence.orient")
val blendedSprayApi = BlendedModule("blended.spray.api")
val blendedTestSupport = BlendedModule("blended.testsupport")
val blendedUpdater = BlendedModule("blended.updater")
val blendedUpdaterConfig = BlendedModule("blended.updater.config")


