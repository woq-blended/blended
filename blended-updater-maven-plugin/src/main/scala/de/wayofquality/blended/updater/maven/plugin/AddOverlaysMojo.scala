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

@Mojo(name = "add-overlays", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST)
class AddOverlaysMojo extends AbstractMojo {

  @Component
  var project: MavenProject = _

  @Parameter(required = true, property = "srcProfile")
  var srcProfile: File = _

  @Parameter(defaultValue = "${project.build.directory}/profile", property = "destDir", required = true)
  var destDir: File = _

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

  /**
   * Create a launch configuration file
   */
  @Parameter
  var createLaunchConfig: File = _

  override def execute() = {
    getLog.debug("Running Mojo add-overlays")

    val targetProfile = new File(destDir, "profile.conf")

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
      "--write-overlays-config"
    ) ++ debugArgs ++ overlayArgs ++ launchConfArgs

    getLog().debug("About to run RuntimeConfigBuilder.run with args: " + profileArgs)

    RuntimeConfigBuilder.run(profileArgs, Some(debugMsg => getLog().debug(debugMsg)))
  }

}