package blended.jms.bridge.internal

import blended.jms.utils.ProviderAware
import blended.testsupport.scalatest.LoggingFreeSpec

class ProviderFilterSpec extends LoggingFreeSpec {

  case class Dummy(
    vendor : String,
    provider : String
  ) extends ProviderAware

  "The Providerfilter should" - {

    "match if filter conditions are correct" in {

      val p1 = Dummy("TestVendor", "TestProvider")

      assert(ProviderFilter("TestVendor", "TestProvider").matches(p1))
      assert(ProviderFilter("TestVendor").matches(p1))
      assert(ProviderFilter("TestVendor", "Test.*").matches(p1))
      assert(ProviderFilter("TestVendor", ".*Provider").matches(p1))
    }

    "not match if filter conditions are not correct" in {

      val p1 = Dummy("TestVendor", "TestProvider")

      assert(!ProviderFilter("foo", "TestProvider").matches(p1))
      assert(!ProviderFilter("foo").matches(p1))
      assert(!ProviderFilter("TestVendor", "blah.*").matches(p1))
    }
  }

}
