package blended.updater.tools.configbuilder

import de.tototec.cmdoption.CmdlineParser
import org.scalatest.FreeSpec

class FeatureBuilderSpec extends FreeSpec {

  "CmdOption validate" in {
    val cp = new CmdlineParser(new FeatureBuilder.Cmdline(), new FeatureBuilder.CmdlineCommon)
    cp.validate()
  }
  
  "--help should succeed" in {
    FeatureBuilder.run(Array("--help"))
  }

}