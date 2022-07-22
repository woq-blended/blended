package blended.updater.config

import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class OverlayStateSpec extends AnyFreeSpec with ScalaCheckPropertyChecks {

  import TestData._

  "OverlayState.fromString should work for known states" in  {
    forAll(overlayStates) { o =>
      assert(OverlayState.fromString(o.state) === Some(o))
    }
  }
}
