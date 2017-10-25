// Polyglot Maven Scala file
// Shared build settings

import scala.collection.immutable

val releaseProfile = Profile(
  id = "release",
  activation = Activation(),
  build = BuildBase(
    plugins = Seq(
      Plugin(
        gav = Plugins.source,
        executions = Seq(
          Execution(
            id = "attach-sources-no-fork",
            phase = "package",
            goals = Seq(
              "jar-no-fork"
            )
          ),
          Execution(
            id = "attach-sources",
            phase = "DISABLE_FORKED_LIFECYCLE_MSOURCES-13",
            goals = Seq(
              "jar-no-fork"
            )
          )
        ),
        configuration = Config(
          includes = Config(
            include = "**/*.java",
            include = "**/*.scala"
          ),
          forceCreation = "true"
        )
      ),
      Plugin(
        gav = Plugins.gpg,
        executions = Seq(
          Execution(
            id = "sign-artifacts",
            phase = "verify",
            goals = Seq(
              "sign"
            )
          )
        )
      ),
      Plugin(
        gav = Plugins.scala,
        executions = Seq(
          Execution(
            id = "attach-doc",
            phase = "package",
            goals = Seq(
              "doc-jar"
            )
          )
        )
      )
    )
  )
)

val coverageProfile = Profile(
  id = "coverage",
  activation = Activation(),
  build = BuildBase(
    plugins = Seq(scoverageMavenPlugin)
  )
)

val genPomXmlProfile = Profile(
  id = "gen-pom-xml",
  activation = Activation(),
  build = BuildBase(
    plugins = Seq(
      // clean: remove generated pom.xml
      Plugin(
        Plugins.clean,
        configuration = Config(
          filesets = Config(
            fileset = Config(
              directory = "${basedir}",
              includes = Config(
                include = "pom.xml"
              )
            )
          )
        )
      ),
      // initialize: generate pom.xml
      polyglotTranslatePlugin
    )
  )
)

val checkDepsProfile = Profile(
  id = "check-deps",
  build = BuildBase(
    plugins = Seq(
      checkDepsPlugin
    )
  )
)

val scalaIdePrefs = """
scala.compiler.installation=2.11
scala.compiler.sourceLevel=2.11
target=jvm-1.8
"""

val eclipseProfile = Profile(
  id = "eclipse",
  build = BuildBase(
    plugins = Seq(
      Plugin(
        gav = Plugins.antrun,
        executions = Seq(
          Execution(
            id = "gen-eclipse-settings",
            goals = Seq("run"),
            phase = "initialize",
            configuration = Config(
              target = Config(
                mkdir = Config(
                  `@dir` = "${project.basedir}/.settings"
                ),
                echo = Config(
                  `@file` = "${project.basedir}/.settings/org.scala-ide.sdt.core.prefs",
                  `@message` = scalaIdePrefs
                )
              )
            )
          )
        )
      )
    )
  )
)

// We define the BlendedModel with some defaults, so that they can be reused
// throughout the build

object BlendedModel {

  // Properties we attach to all BlendedModels
  val defaultProperties: Map[String, String] =
    Map(
      "project.build.sourceEncoding" -> "UTF-8",
      "bundle.symbolicName" -> "${project.artifactId}",
      "bundle.namespace" -> "${project.artifactId}",
      "java.version" -> BlendedVersions.javaVersion,
      "scala.version" -> scalaVersion.binaryVersion,
      "blended.version" -> BlendedVersions.blendedVersion
    )

  // Profiles we attach to all BlendedModels
  val defaultProfiles = Seq(
    releaseProfile,
    coverageProfile,
    genPomXmlProfile,
    eclipseProfile,
    checkDepsProfile
  )

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
      gav = Plugins.buildHelper,
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
    ),
    Plugin(
      gav = Plugins.enforcer,
      executions = Seq(
        Execution(
          id = "enforce-maven",
          goals = Seq(
            "enforce"
          ),
          configuration = Config(
            rules = Config(
              requireMavenVersion = Config(
                version = "3.0.5"
              )
            )
          )
        )
      )
    ),
    Plugin(
      gav = Plugins.compiler,
      configuration = Config(
        source = "${java.version}",
        target = "${java.version}",
        encoding = "${project.build.sourceEncoding}",
        fork = "true"
      )
    )
  )

  val distMgmt = DistributionManagement(
    repository = DeploymentRepository(
      id = "ossrh",
      url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
    ),
    snapshotRepository = DeploymentRepository(
      id = "ossrh",
      url = "https://oss.sonatype.org/content/repositories/snapshots/"
    )
  )

  def apply(
    gav: Gav,
    ciManagement: CiManagement = null,
    contributors: immutable.Seq[Contributor] = Nil,
    dependencyManagement: DependencyManagement = null,
    dependencies: immutable.Seq[Dependency] = Nil,
    description: String = null,
    developers: immutable.Seq[Developer] = Nil,
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
    profiles: immutable.Seq[Profile] = Seq.empty,
    properties: Map[String, String] = Map.empty,
    repositories: immutable.Seq[Repository] = Nil,
    parent: Parent = null,
    resources: Seq[Resource] = Seq.empty,
    testResources: Seq[Resource] = Seq.empty,
    plugins: Seq[Plugin] = Seq.empty,
    pluginManagement: Seq[Plugin] = null) = {
    if (parent != null) println(s"Project with parent: ${gav}")
    val theBuild = {
      val usedPlugins = plugins ++ defaultPlugins
      Option(Build(
        resources = resources ++ defaultResources,
        testResources = testResources ++ defaultTestResources,
        plugins = usedPlugins,
        pluginManagement = PluginManagement(plugins = Option(pluginManagement).getOrElse(usedPlugins))
      ))
    }

    new Model(
      gav = gav,
      build = theBuild,
      ciManagement = Option(ciManagement),
      contributors = contributors,
      dependencyManagement = Option(dependencyManagement).orElse(Option(DependencyManagement(dependencies))),
      dependencies = dependencies,
      description = Option(description),
      developers = defaultDevelopers ++ developers,
      distributionManagement = Option(distMgmt),
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

// Support for building features and containers

case class FeatureBundle(
    dependency: Dependency,
    startLevel: Integer = -1,
    start: Boolean = false) {
  override def toString: String = {

    val gav = dependency.gav

    val builder: StringBuilder = new StringBuilder("{ ")

    builder.append("url=\"")
    builder.append("mvn:")

    builder.append(gav.groupId.get)
    builder.append(":")
    builder.append(gav.artifactId)
    builder.append(":")

    // if true, we render the long form with 5 parts (4 colons) instead of 3 parts (2 colons)
    val longForm = dependency.classifier.isDefined || !dependency.`type`.equals("jar")

    if (longForm) {
      builder.append(dependency.classifier.getOrElse(""))
      builder.append(":")
    }

    builder.append(gav.version.get)

    if (longForm) {
      builder.append(":")
      builder.append(dependency.`type`)
    }

    builder.append("\"")

    if (startLevel >= 0) builder.append(s", startLevel=${startLevel}")
    if (start) builder.append(", start=true")

    builder.append(" }")

    builder.toString()
  }
}

// Create the String content of a feature file from a sequence of FeatureBundles

def featureDependencies(features: Map[String, Seq[FeatureBundle]]): Seq[Dependency] = {
  features.values.flatten.map(_.dependency.copy(exclusions = Seq("*" % "*"))).toList
}

// This is the content of the feature file
def featureFile(name: String, features: Seq[FeatureBundle]): String = {

  val prefix = "\"\"\"name=\"" + name + "\"\nversion=\"${project.version}\"\n"

  val bundles = features.map(_.toString).mkString(
    "bundles = [\n", ",\n", "\n]\n\"\"\"")

  prefix + bundles
}

def generateFeatures(features: Map[String, Seq[FeatureBundle]]) = {

  val writeFiles = features.map {
    case (key, bundles) =>
      """
ScriptHelper.writeFile(new File(project.getBasedir(), "target/classes/""" + key + """.conf"), """ + featureFile(key, bundles) + """)
"""
  }.mkString("import java.io.File\n", "\n", "")

  scriptHelper + writeFiles
}

object Feature {
  def apply(name: String) = Dependency(
    blendedLauncherFeatures,
    `type` = "conf",
    classifier = name
  )
}

def featuresMavenPlugins(features: Map[String, Seq[FeatureBundle]]) = Seq(
  Plugin(
    gav = Plugins.scala,
    executions = Seq(
      Execution(
        id = "build-product",
        phase = "generate-resources",
        goals = Seq(
          "script"
        ),
        configuration = Config(
          script = generateFeatures(features)
        )
      )
    )
  ),
  Plugin(
    blendedUpdaterMavenPlugin,
    executions = Seq(
      Execution(
        id = "make-features",
        phase = "compile",
        goals = Seq(
          "build-features"
        ),
        configuration = Config(
          srcFeatureDir = "${project.build.directory}/classes",
          resolveFromDependencies = "true"
        )
      )
    )
  )
)

object BlendedProfileResourcesContainer {
  def apply(
    gav: Gav,
    properties: Map[String, String] = Map.empty) = {
    
    BlendedModel(
      gav = gav,
      packaging = "jar",
      description = "Container Profile Resources " + gav.artifactId,
      properties = properties,
      plugins = Seq(
        skipDefaultJarPlugin,
        Plugin(
          gav = Plugins.assembly,
          executions = Seq(
            Execution(
              id = "assemble",
              phase = "package",
              goals = Seq("single")
            )
          ),
          configuration = Config(
            descriptors = Config(
              descriptor = "src/main/assembly/resources.xml"
            ),
            appendAssemblyId = false
          )
        )
      ))
  }
}

/**
 * Maven project factory for Blended Container projects.
 *
 * Expectations
 * ** `src/main/resources/container` - Container files, will go into `<container>
 * ** `src/main/resources/profile` - Profiles files, will go into `<container>/profiles/<name>/<version>`
 * ** `src/main/assembly/full-nojre.xml` - Assembly config for a complete container archive as `.tar.gz` and `.zip`
 * ** `src/main/assembly/resources.xml` - Assembly config for the resources-files of the container as `.zip`. This is essentially the content of `src/main/resources/profile`
 * ** `src/main/assembly/product.xml` - Assembly config (TODO, check if corrent und document where it is used)
 * ** `src/main/assembly/deploymentpack.xml` - Deploymentpack, used to deploy/register the profile into the management server
 */
object BlendedContainer {

  def apply(
    gav: Gav,
    description: String,
    properties: Map[String, String] = Map.empty,
    features: immutable.Seq[Dependency] = Seq.empty,
    blendedProfileResouces: Gav = null) = {

    BlendedModel(
      gav = gav,
      description = description,
      packaging = "jar",
      prerequisites = Prerequisites(
        maven = "3.3.3"
      ),
      properties = Map(
        "profile.name" -> gav.artifactId,
        "profile.version" -> gav.version.get
      ) ++ properties,
      dependencies = features ++ Seq(
        Dependency(
          blendedLauncher,
          `type` = "zip",
          classifier = "bin"
        )
      ) ++
        // the profile resources dep as ZIP file
        Option(blendedProfileResouces).map(g => Dependency(gav = g, `type` = "zip")).toList,
      plugins = Seq(
        Plugin(
          gav = blendedUpdaterMavenPlugin,
          executions = Seq(
            Execution(
              id = "materialize-profile",
              phase = "compile",
              goals = Seq(
                "materialize-profile"
              ),
              configuration = Config(
                srcProfile = "${project.build.directory}/classes/profile/profile.conf",
                destDir = "${project.build.directory}/classes/profile",
                explodeResources = true
              )
            )
          )
        ),
        Plugin(
          gav = Plugins.dependency,
          executions = Seq(
            Execution(
              id = "unpack-launcher",
              phase = "compile",
              goals = Seq(
                "unpack"
              ),
              configuration = Config(
                artifactItems = Config(
                  artifactItem = Config(
                    groupId = "${project.groupId}",
                    artifactId = "blended.launcher",
                    classifier = "bin",
                    `type` = "zip",
                    outputDirectory = "${project.build.directory}/launcher"
                  )
                )
              )
            )
          )
        ),
        Plugin(
          gav = Plugins.scala,
          executions = Seq(
            Execution(
              id = "build-product",
              phase = "generate-resources",
              goals = Seq(
                "script"
              ),
              configuration = Config(
                script = scriptHelper + """
  import java.io.File

  // make launchfile

  val tarLaunchFile = new File(project.getBasedir(), "target/classes/container/launch.conf")

  val launchConf =
    "profile.baseDir=${BLENDED_HOME}/profiles\n" +
    "profile.name=""" + gav.artifactId + """\n" +
    "profile.version=""" + gav.version.get + """"

  ScriptHelper.writeFile(tarLaunchFile, launchConf)

  // make overlays base.conf

  val baseConfFile = new File(project.getBasedir(), "target/classes/profile/overlays/base.conf")
  ScriptHelper.writeFile(baseConfFile, "overlays = []")
  """
              )
            )
          )
        ),
        Plugin(
          gav = Plugins.assembly,
          executions = Seq(
            Execution(
              id = "assemble",
              phase = "package",
              goals = Seq(
                "single"
              )
            )
          ),
          configuration = Config(
            tarLongFileMode = "gnu",
            descriptors = Config(
              descriptor = "src/main/assembly/full-nojre.xml",
              descriptor = "src/main/assembly/product.xml",
              descirptor = "src/main/assembly/deploymentpack.xml"
            )
          )
        ),
        skipDefaultJarPlugin,
        skipNexusStagingPlugin
      )
    )

  }

}

/**
 * The blended docker container template.
 */
object BlendedDockerContainer {
  def apply(
    gav: Gav,
    image: Dependency,
    folder: String,
    ports: List[Int] = List.empty) = BlendedModel(
    gav = gav,
    packaging = "jar",
    description = "Packaging the launcher sample container into a docker image.",
    dependencies = Seq(image),

    properties = Map(
      "docker.src.type" -> image.`type`,
      "docker.target" -> folder,
      "docker.src.version" -> image.gav.version.get,
      "docker.src.artifactId" -> image.gav.artifactId,
      "docker.src.classifier" -> image.classifier.get,
      "docker.src.groupId" -> image.gav.groupId.get
    ),

    plugins = Seq(
      Plugin(
        Plugins.dependency,
        executions = Seq(
          Execution(
            id = "extract-blended-container",
            phase = "process-resources",
            goals = Seq(
              "copy-dependencies"
            ),
            configuration = Config(
              includeScope = "provided",
              outputDirectory = "${project.build.directory}/docker/${docker.target}"
            )
          )
        )
      ),
      Plugin(
        gav = Plugins.scala,
        executions = Seq(
          Execution(
            id = "prepare-docker",
            phase = "generate-resources",
            goals = Seq(
              "script"
            ),
            configuration = Config(
              script = scriptHelper + """
  import java.io.File

  // make Dockerfile

  val dockerfile = new File(project.getBasedir() + "/src/main/docker/""" + folder + """", "Dockerfile")

  val dockerconf =
    "FROM atooni/blended-base:latest\n" +
    "MAINTAINER Blended Team version: """ + gav.version.get + """\n" +
    "ADD ${docker.src.artifactId}-${docker.src.version}-${docker.src.classifier}.${docker.src.type} /opt\n" +
    "RUN ln -s /opt/${docker.src.artifactId}-${docker.src.version} /opt/${docker.target}\n" +
    "RUN chown -R blended.blended /opt/${docker.src.artifactId}-${project.version}\n" +
    "RUN chown -R blended.blended /opt/${docker.target}\n" +
    "USER blended\n" +
    "ENV JAVA_HOME /opt/java\n" +
    "ENV PATH ${PATH}:${JAVA_HOME}/bin\n" +
    "ENTRYPOINT [\"/bin/sh\", \"/opt/${docker.target}/bin/blended.sh\"]\n" +
    """" + ports.map(p => "EXPOSE " + p + "\\n").mkString + """"

  ScriptHelper.writeFile(dockerfile, dockerconf)

  """
            )
          )
        )
      ),
      dockerMavenPlugin
    )
  )
}
