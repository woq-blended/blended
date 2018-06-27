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

val eclipseProfile = Profile(
  id = "eclipse",
  build = BuildBase(
    plugins = Seq(
      Plugin(
        Plugins.trEclipse,
        configuration = Config(
          // alternativeOutput = "target-ide"
          outputDirectory = "${basedir}/target-ide/classes",
          testOutputDirectory = "${basedir}/target-ide/test-classes"
        )
      )
    )
  )
)

/**
 * Removed duplicate plugins by merging their configurations and executions.
 */
def sanitizePlugins(plugins: Seq[Plugin]): Seq[Plugin] = plugins.foldLeft(Seq[Plugin]()) { (l, r) =>
  l.find(p => p.gav == r.gav) match {
    case None => l ++ Seq(r)
    case Some(existing) => l.map { p =>
      if (p == existing) {
        Plugin(
          gav = p.gav,
          dependencies = p.dependencies ++ r.dependencies,
          extensions = p.extensions || r.extensions,
          inherited = p.inherited || r.inherited,
          executions = p.executions ++ r.executions,
          configuration = (p.configuration, r.configuration) match {
            case (None, None) => null
            case (Some(c), None) => c
            case (None, Some(c)) => c
            case (Some(a), Some(b)) => new Config(a.elements ++ b.elements)
          }
        )
      } else p
    }
  }
}

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
      "scala.version" -> scalaVersion.version,
      "scala.binary.version" -> scalaVersion.binaryVersion,
      "scalaImportVersion" -> "[${scala.binary.version},${scala.binary.version}.50)",
      "blended.version" -> BlendedVersions.blendedVersion
    )

  // Profiles we attach to all BlendedModels
  val defaultProfiles = Seq(releaseProfile, genPomXmlProfile, checkDepsProfile, eclipseProfile)

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
                version = "3.3.1"
              )
            )
          )
        )
      )
    ),
    Plugin(
      gav = Plugins.compiler,
      configuration = Config(
        skipMain = "true",
        skip = "true"
      )
    ),
    Plugin(
      gav = Plugins.scoverage,
      configuration = Config(
        aggregate = "true",
        highlighting = "true"
      )
    ),
    Plugin(
      gav = Plugins.antrun,
      executions = Seq(
        antrunExecution_logbackXml
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
    pureDependencies: immutable.Seq[Dependency] = Nil,
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
    prerequisites: Prerequisites = null,
    profiles: immutable.Seq[Profile] = Seq.empty,
    properties: Map[String, String] = Map.empty,
    repositories: immutable.Seq[Repository] = Nil,
    resources: Seq[Resource] = Seq.empty,
    testResources: Seq[Resource] = Seq.empty,
    plugins: Seq[Plugin] = Seq.empty,
    pluginManagement: Seq[Plugin] = null
  ) = {

    val theBuild = {
      val usedPlugins = sanitizePlugins(plugins ++ defaultPlugins)
      Build(
        resources = resources ++ defaultResources,
        testResources = testResources ++ defaultTestResources,
        plugins = usedPlugins,
        pluginManagement = PluginManagement(plugins = Option(pluginManagement).getOrElse(usedPlugins))
      )
    }

    val allDeps = distinctDependencies(pureDependencies.map(_.intransitive) ++ dependencies)

    Model(
      gav = gav,
      build = theBuild,
      ciManagement = ciManagement,
      contributors = contributors,
      dependencyManagement = Option(dependencyManagement).getOrElse(DependencyManagement(allDeps)),
      dependencies = allDeps,
        description = description,
      developers = defaultDevelopers ++ developers,
      distributionManagement = distMgmt,
      inceptionYear = inceptionYear,
      issueManagement = issueManagement,
      licenses = defaultLicenses ++ licenses,
      mailingLists = mailingLists,
      modelEncoding = modelEncoding,
      modelVersion = "4.0.0",
      modules = modules,
      name = "${project.artifactId}",
      organization = organization,
      packaging = packaging,
      pluginRepositories = pluginRepositories,
      prerequisites = prerequisites,
      profiles = defaultProfiles ++ profiles,
      properties = defaultProperties ++ properties,
      repositories = defaultRepositories ++ repositories,
      scm = scm,
      url = "https://github.com/woq-blended/blended"
    )
  }
}

// Support for building features and containers

case class FeatureDef(name: String, features: Seq[String] = Seq(), bundles: Seq[FeatureBundle])

case class FeatureBundle(
  dependency: Dependency,
  startLevel: Integer = -1,
  start: Boolean = false
) {
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

def featureDependencies(features: Seq[FeatureDef]): Seq[Dependency] =
  distinctDependencies(features.flatMap(_.bundles.map(_.dependency)))

def distinctDependencies(deps: Seq[Dependency]): Seq[Dependency] =
  deps.foldLeft(List[Dependency]()) { (ds, n) =>
    if (ds.exists(d =>
      d.gav.groupId == n.gav.groupId &&
        d.gav.artifactId == n.gav.artifactId &&
        d.gav.version == n.gav.version &&
        d.classifier == n.classifier &&
        d.scope == n.scope)) ds
    else n :: ds
  }.reverse

// This is the content of the feature file
def featureFile(feature: FeatureDef): String = {

  val prefix = "name=\"" + feature.name + "\"\nversion=\"${project.version}\"\n"

  val bundles = feature.bundles.map(_.toString).mkString(
    "bundles = [\n", ",\n", "\n]\n"
  )

  val featureRefs =
    if (feature.features.isEmpty) ""
    else feature.features.map(f => s"""{ name="${f}", version="$${project.version}" }""").mkString(
      "features = [\n", ",\n", "\n]\n"
    )

  "\"\"\"" + prefix + featureRefs + bundles + "\"\"\""
}

def generateFeatures(features: Seq[FeatureDef]) = {

  val writeFiles = features.map { feature =>
    """
ScriptHelper.writeFile(new File(project.getBasedir(), "target/classes/""" + feature.name + """.conf"), """ + featureFile(feature) + """)
"""
  }.mkString("import java.io.File\n", "\n", "")

  scriptHelper + writeFiles
}

object Feature {
  def apply(name: String) = Dependency(
    Blended.launcherFeatures,
    `type` = "conf",
    classifier = name
  )
}

def featuresMavenPlugins(features: Seq[FeatureDef]) = Seq(
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
    Blended.updaterMavenPlugin,
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

/**
 *  A Blended project containing resources for a Blended Container Profile.
 */
object BlendedProfileResourcesContainer {
  def apply(
    gav: Gav,
    properties: Map[String, String] = Map.empty
  ) = {

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
        ),
        skipDeployPlugin,
        skipNexusStagingPlugin
      )
    )
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
    blendedProfileResouces: Gav = null,
    overlayDir: String = null,
    overlays: Seq[String] = Seq.empty
  ) = {

    BlendedModel(
      gav = gav,
      description = description,
      packaging = "jar",
      properties = Map(
        "profile.name" -> gav.artifactId,
        "profile.version" -> gav.version.get
      ) ++ properties,
      dependencies = features ++ Seq(
        Dependency(
          Blended.launcher,
          `type` = "zip",
          classifier = "bin"
        )
      ) ++
        // the profile resources dep as ZIP file
        Option(blendedProfileResouces).map(g => Dependency(gav = g, `type` = "zip")).toList,
      plugins = Seq(
        Plugin(
          gav = Blended.updaterMavenPlugin,
          executions = Seq(
            // Materialize a complete profile based on profile.conf and maven dependencies
            Execution(
              id = "materialize-profile",
              phase = "compile",
              goals = Seq(
                "materialize-profile"
              ),
              configuration = Config(
                srcProfile = "${project.build.directory}/classes/profile/profile.conf",
                destDir = "${project.build.directory}/classes/profile",
                explodeResources = true,
                createLaunchConfig = "${project.build.directory}/classes/container/launch.conf",
                overlays = new Config(overlays.map(o => "overlay" -> Some(Option(overlayDir).getOrElse("") + "/" + o)))
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
          gav = Plugins.antrun,
          executions = Seq(
            Execution(
              id = "unpack-full-nojre",
              phase = "integration-test",
              goals = Seq("run"),
              configuration = Config(
                target = Config(
                  unzip = Config(
                    `@src` = "${project.build.directory}/${project.artifactId}-${project.version}-full-nojre.zip",
                    `@dest` = "${project.build.directory}"

                  )
                )
              )
            )
          )
        ),
        Plugin(
          gav = Plugins.assembly,
          executions = Seq(
            // Build the various assemblies
            Execution(
              id = "assemble",
              phase = "package",
              goals = Seq(
                "single"
              ),
              configuration = Config(
                tarLongFileMode = "gnu",
                descriptors = Config(
                  descriptor = "src/main/assembly/full-nojre.xml",
                  descriptor = "src/main/assembly/product.xml",
                  descirptor = "src/main/assembly/deploymentpack.xml"
                )
              )
            )
          )
        ),
        skipDefaultJarPlugin,
        skipDeployPlugin,
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
    ports: List[Int] = List.empty,
    overlayDir: String = null,
    overlays: Seq[String] = Seq.empty
  ) = {

    val containerDir = "${project.build.directory}/docker/${docker.target}/container"
    val imageDir = image.gav.artifactId + "-" + image.gav.version.get
    val profileDir = imageDir + "/profiles/" + image.gav.artifactId + "/" + image.gav.version.get
    val profileConf = profileDir + "/profile.conf"

    val dockerOverlayCmd =
      if (overlays.isEmpty) "# no extra overlays"
      else "ADD container/" + imageDir + " /opt/${docker.target}"

    val addPlugins =
      if (overlays.isEmpty) Seq()
      else Seq(
        // unpack
        Plugin(
          Plugins.dependency,
          executions = Seq(
            Execution(
              id = "dependency-unpack-blended-container",
              phase = "process-resources",
              goals = Seq(
                "unpack"
              ),
              configuration = Config(
                artifactItems = Config(
                  artifactItem = Config(
                    groupId = image.gav.groupId.get,
                    artifactId = image.gav.artifactId,
                    version = image.gav.version.get,
                    `type` = image.`type`,
                    classifier = image.classifier.getOrElse(""),
                    outputDirectory = containerDir,
                    includes = profileConf
                  )
                )
              )
            )
          )
        ),
        // materialize overlays config files into profile
        Plugin(
          gav = Blended.updaterMavenPlugin,
          executions = Seq(
            // Materialize a complete profile based on profile.conf and maven dependencies
            Execution(
              id = "updater-add-overlays-to-container",
              phase = "compile",
              goals = Seq(
                "add-overlays"
              ),
              configuration = Config(
                srcProfile = containerDir + "/" + profileConf,
                destDir = containerDir + "/" + profileDir,
                createLaunchConfig = containerDir + "/" + imageDir + "/launch.conf",
                overlaysDir = overlayDir,
                overlays = new Config(overlays.map(o => "overlay" -> Some(Option(overlayDir).getOrElse("") + "/" + o)))
              )
            )
          )
        )
      )

    BlendedModel(
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

      plugins = sanitizePlugins(addPlugins ++ Seq(
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
                outputDirectory = "${project.build.directory}/docker/${docker.target}",
                excludeTransitive = "true"
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
      """" + dockerOverlayCmd + """\n" +
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
      ))
    )
  }
}
