package blended.updater.tools.configbuilder

import de.tototec.cmdoption.CmdlineParser
import org.scalatest.FreeSpec

class RuntimConfigBuilderSpec extends FreeSpec {

  "CmdOption validate" in {
    val cp = new CmdlineParser(new RuntimeConfigBuilder.CmdOptions())
    cp.validate()
  }

  "--help should succeed" in {
    RuntimeConfigBuilder.run(Array("--help"))
  }

}
