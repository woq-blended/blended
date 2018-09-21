package blended.security.scep.standalone

import java.util.concurrent.TimeoutException

import blended.testsupport.scalatest.LoggingFreeSpec
import de.tototec.cmdoption.CmdlineParser

class ScepClientAppTest extends LoggingFreeSpec {

  "Cmdline validate" in {
    val cmdline = new Cmdline()
    val cp = new CmdlineParser(cmdline)
    cp.validate()
  }

  "Cmdline help" in {
    val ex = intercept[ExitAppException] {
      ScepClientApp.run(Array("--help"))
    }
    assert(ex.exitCode === 0)
    assert(ex.errMsg === None)
  }

  "App should fail with TimeoutException when no config is present" in {
     val ex = intercept[ExitAppException] {
      ScepClientApp.run(Array("--refresh-certs"))
    }
    assert(ex.exitCode === 1)
    assert(ex.getCause().getClass() === classOf[TimeoutException])
  }

}
