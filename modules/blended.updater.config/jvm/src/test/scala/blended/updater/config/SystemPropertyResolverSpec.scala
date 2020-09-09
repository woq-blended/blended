package blended.updater.config

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


class SystemPropertyResolverSpec extends AnyWordSpec with Matchers {

  "The System Property Resolver" should {

    "Resolve an empty set" in {
      SystemPropertyResolver.resolve(Map.empty) should have size 0
    }

    "Resolve a map without place holders" in {
      val m = Map("foo" -> "bar", "prop" -> "value")
      SystemPropertyResolver.resolve(m) should be(m)
    }

    "Resolve a map with simple placeholders" in {
      SystemPropertyResolver.resolve(Map("foo" -> "bla${prop}bla", "prop" -> "bla")) should be(Map("foo" -> "blablabla", "prop" -> "bla"))
    }

    "Resolve a map with chained placeholders" in {
      val m = Map("foo" -> "${prop1} ${prop1} ${prop1}", "prop1" -> "${prop2}", "prop2" -> "bar")

      SystemPropertyResolver.resolve(m) should be(Map("foo" -> "bar bar bar", "prop1" -> "bar", "prop2" -> "bar"))
    }
  }

}
