package blended.launcher

import org.scalatest.FreeSpec

class GenericLauncherTest extends FreeSpec {
  "Launcher prints help without errors" in {
    val ex = intercept[LauncherException] {
      Launcher.run(Array("--help"))
    }
    assert(ex.errorCode === 0)
  }
}

