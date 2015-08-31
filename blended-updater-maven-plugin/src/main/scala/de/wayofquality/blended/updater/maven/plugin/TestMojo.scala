package de.wayofquality.blended.updater.maven.plugin

import java.io.File
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter

@Mojo(name = "test")
class TestMojo extends AbstractMojo {
  
  @Parameter(defaultValue = "${project.basedir}")
  var baseDirectory: File = _

  override def execute() = {
    getLog.debug("Running Mojo test");
    getLog.info("Base dir: " + baseDirectory)
     
    //TODO
  }

}