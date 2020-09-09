package blended.launcher

import org.scalatest.freespec.AnyFreeSpec

class GenericLauncherTest extends AnyFreeSpec {
  "Launcher prints help without errors" in {
    val ex = intercept[LauncherException] {
      Launcher.run(Array("--help"))
    }
    assert(ex.errorCode === 0)
  }
}

