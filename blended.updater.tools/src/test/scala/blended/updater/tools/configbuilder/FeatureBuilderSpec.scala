package blended.updater.tools.configbuilder

import de.tototec.cmdoption.CmdlineParser
import org.scalatest.freespec.AnyFreeSpec

class FeatureBuilderSpec extends AnyFreeSpec {

  "CmdOption validate" in {
    val cp = new CmdlineParser(new FeatureBuilder.Cmdline(), new FeatureBuilder.CmdlineCommon)
    cp.validate()
  }

  "--help should succeed" in {
    FeatureBuilder.run(Array("--help"))
  }

}
