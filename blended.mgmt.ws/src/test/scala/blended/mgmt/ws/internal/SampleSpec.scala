package blended.mgmt.ws.internal

import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import org.scalatest.FreeSpec

class SampleSpec extends FreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  "The Web socket server should" - {

    "accept clients with a valid token" in {

      pending
    }
  }

}
