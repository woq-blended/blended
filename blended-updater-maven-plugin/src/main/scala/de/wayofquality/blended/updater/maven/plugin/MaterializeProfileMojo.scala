package de.wayofquality.blended.updater.maven.plugin

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.ResolutionScope
import java.io.File
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.project.MavenProject
import org.apache.maven.plugins.annotations.Parameter
import blended.updater.tools.configbuilder._
import scala.collection.JavaConverters._
import java.{ util => ju }

@Mojo(name = "materialize-profile", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST)
class MaterializeProfileMojo extends AbstractMojo {

  @Component
  var project: MavenProject = _

  @Parameter(required = true, property = "srcProfile")
  var srcProfile: File = _

  @Parameter(defaultValue = "${project.build.directory}/profile", property = "destDir", required = true)
  var destDir: File = _

  //  @Parameter(defaultValue = "false")
  //  var makeProfileAndVersionDir: Boolean = _

  @Parameter(property = "localRepositoryUrl")
  var localRepositoryUrl: String = _

  @Parameter(defaultValue = "compile", property = "dependencyScope")
  var dependencyScope: String = _

  @Parameter(defaultValue = "conf", property = "dependencyType")
  var dependencyType: String = _

  @Parameter(defaultValue = "false", property = "explodeResources")
  var explodeResources: Boolean = false

  @Parameter(defaultValue = "false", property = "blended-updater.debug")
  var debug: Boolean = false

  /**
   * Directory where the overlays files will we searched.
   */
  @Parameter
  var overlaysDir: File = _

  /**
   * The given set of overlays (config files) will be added to the materialized profile.
   */
  @Parameter
  var overlays: ju.List[File] = _

  // TODO add filter for conf dependencies

  /**
   * Resolve all artifacts with mvn URLs only from the dependencies of the project.
   */
  @Parameter(property = "resolveFromDependencies", defaultValue = "false")
  var resolveFromDependencies: Boolean = _

  /**
   * Create a launch configuration file
   */
  @Parameter
  var createLaunchConfig: File = _

  override def execute() = {
    getLog.debug("Running Mojo materialize-profile")

    val targetProfile = new File(destDir, "profile.conf")

    val confArtifacts = project.getDependencyArtifacts.asScala.
      filter(a => a.getScope() == dependencyScope && a.getType() == dependencyType)
    getLog.info(s"Feature artifacts: ${confArtifacts.mkString(", ")}")
    val featureFiles = confArtifacts.toSeq.map(_.getFile())
    val featureArgs = featureFiles.toArray.flatMap { f =>
      Array("-r", f.getAbsolutePath)
    }

    getLog.debug("feature args: " + featureArgs.mkString("Array(", ", ", ")"))

    val localRepoUrl = Option(localRepositoryUrl).getOrElse(project.getProjectBuildingRequest.getLocalRepository.getUrl)
    val remoteRepoUrls = project.getRepositories.asScala.map(r => r.getUrl)

    val repoArgs = if (resolveFromDependencies) {
      project.getArtifacts.asScala.toArray.flatMap { a =>
        Array(
          "--maven-artifact",
          s"${a.getGroupId}:${a.getArtifactId}:${Option(a.getClassifier).filter(_ != "jar").getOrElse("")}:${a.getVersion}:${Option(a.getType).getOrElse("")}",
          a.getFile.getAbsolutePath
        )
      }
    } else {
      Array("--maven-url", localRepoUrl) ++ remoteRepoUrls.toArray.flatMap(u => Array("--maven-url", u))
    }
    getLog.debug("repo args: " + repoArgs.mkString("Array(", ", ", ")"))

    val explodeResourcesArgs = if (explodeResources) Array("--explode-resources") else Array[String]()

    val debugArgs = if (debug) Array("--debug") else Array[String]()

    val overlayArgs =
      // prepend base dir if set
      Option(overlays).getOrElse(ju.Collections.emptyList()).asScala.map { o =>
        Option(overlaysDir) match {
          case None => o
          case _ if o.isAbsolute() => o
          case Some(f) => new File(f, o.getPath())
        }
      }.
        // create args
        flatMap(o => Seq("--add-overlay-file", o.getAbsolutePath())).toArray

    val launchConfArgs = Option(createLaunchConfig).toList.flatMap(cf => Seq("--create-launch-config", cf.getPath())).toArray

    val profileArgs = Array(
      "-f", srcProfile.getAbsolutePath,
      "-o", targetProfile.getAbsolutePath,
      "--download-missing",
      "--update-checksums",
      "--write-overlays-config"
    ) ++ debugArgs ++ featureArgs ++ repoArgs ++ explodeResourcesArgs ++ overlayArgs ++ launchConfArgs

    getLog().debug("About to run RuntimeConfigBuilder.run with args: " + profileArgs)

    RuntimeConfigBuilder.run(profileArgs, Some(debugMsg => getLog().debug(debugMsg)))
  }

}