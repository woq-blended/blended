package blended.updater.tools.configbuilder

import de.tototec.cmdoption.CmdlineParser
import org.scalatest.freespec.AnyFreeSpec

class ProfileBuilderSpec extends AnyFreeSpec {

  "CmdOption validate" in {
    val cp = new CmdlineParser(new ProfileBuilder.CmdOptions())
    cp.validate()
  }

  "--help should succeed" in {
    ProfileBuilder.run(Array("--help"))
  }

}
