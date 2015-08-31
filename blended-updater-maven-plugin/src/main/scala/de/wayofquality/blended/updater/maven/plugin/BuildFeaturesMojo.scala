package de.wayofquality.blended.updater.maven.plugin

import java.io.File
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.Component
import blended.updater.tools.configbuilder._
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.versioning.VersionRange
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.project.MavenProject
import org.apache.maven.project.artifact.AttachedArtifact
import org.apache.maven.execution.BuildFailure
import org.apache.maven.BuildFailureException
import scala.collection.JavaConverters._

@Mojo(name = "build-features", threadSafe = true)
class BuildFeaturesMojo extends AbstractMojo {

  @Component
  var project: MavenProject = _

  @Parameter(defaultValue = "${project.basedir}")
  var baseDirectory: File = _

  @Parameter(property = "localRepositoryUrl")
  var localRepositoryUrl: String = _

  @Parameter(required = true, property = "srcFeatureDir")
  var srcFeatureDir: File = _

  @Parameter(defaultValue = "${project.build.directory}/features", property = "destFeatureDir")
  var destFeatureDir: File = _

  @Parameter(defaultValue = ".conf", property = "featureFileSuffix")
  var featureFileSuffix: String = _

  @Parameter(defaultValue = "true", property = "attachFeatures")
  var attach: Boolean = true

  @Parameter(defaultValue = "conf", property = "attachType")
  var attachType: String = _
  
  override def execute() = {
    getLog.debug("Running Mojo build-features");
    getLog.info("Base dir: " + baseDirectory)

    //TODO
    //    val srcFeatureDir = new File(project.getBasedir, "/target/classes")
    //    val destFeatureDir = new File(project.getBasedir, "target/features")

    getLog.debug(s"Project: ${project}")
    getLog.debug(s"Project repositories: ${project.getRepositories}")
    getLog.debug(s"Project properties: ${project.getProperties}")
    getLog.debug(s"Project building request: ${project.getProjectBuildingRequest}")
    getLog.debug(s"Project local repository: ${project.getProjectBuildingRequest.getLocalRepository}")

    val localRepoUrl = Option(localRepositoryUrl).getOrElse(project.getProjectBuildingRequest.getLocalRepository.getUrl)
    val remoteRepoUrls = project.getRepositories.asScala.map(r => r.getUrl)

    val features = Option(srcFeatureDir.listFiles()).getOrElse(Array()).filter(f => f.getName.endsWith(featureFileSuffix))
    if (features.isEmpty) throw new BuildFailureException(s"No feature files found in dir: ${srcFeatureDir}")
    getLog.debug(s"About to process feature files: ${features.map(_.getName).mkString(", ")}")

    val targetFeatureFiles = features.map { featureFile =>
      val targetFile = new File(destFeatureDir, featureFile.getName())
      println(s"Processing feature: ${featureFile}")
      val args = Array(
        "--debug",
        "-f", featureFile.getAbsolutePath(),
        "-o", targetFile.getAbsolutePath(),
        "--work-dir", new File("target/downloads").getAbsolutePath(),
        "--discard-invalid",
        "--download-missing",
        "--update-checksums",
        "--maven-dir", localRepoUrl
      ) ++ remoteRepoUrls.toArray.flatMap(u => Array("--maven-dir", u))
      println(s"Invoking FeatureBuilder with args: ${args.mkString(" ")}")

      FeatureBuilder.run(args)
      targetFile
    }

    getLog.info(s"Produced: ${targetFeatureFiles.mkString(", ")}")

    if (attach) {
      targetFeatureFiles.foreach { featureFile =>
        val name = featureFile.getName
        val classifier = name.substring(0, name.length - featureFileSuffix.length)
        getLog.info(s"Attaching as artifact: ${classifier}")

        val handler = new DefaultArtifactHandler("conf")
        // val artifact = new DefaultArtifact(project.getGroupId, project.getArtifactId, VersionRange.createFromVersion(project.getVersion), "compile", "conf", classifier, handler)
        val artifact = new AttachedArtifact(project.getArtifact(), attachType, classifier, handler)
        artifact.setFile(featureFile)
        artifact.setResolved(true)
        project.addAttachedArtifact(artifact)
      }
    }
  }

}