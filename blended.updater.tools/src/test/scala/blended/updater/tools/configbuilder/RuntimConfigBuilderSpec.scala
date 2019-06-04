package blended.updater.tools.configbuilder

import org.scalatest.FreeSpec
import de.tototec.cmdoption.CmdlineParser

class RuntimConfigBuilderSpec extends FreeSpec {

  "CmdOption validate" in {
    val cp = new CmdlineParser(new RuntimeConfigBuilder.CmdOptions())
    cp.validate()
  }

  "--help should succeed" in {
    RuntimeConfigBuilder.run(Array("--help"))
  }

}
