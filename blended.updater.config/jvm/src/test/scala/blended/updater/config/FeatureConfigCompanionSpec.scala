package blended.updater.config

import scala.util.Success

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class FeatureConfigCompanionSpec extends LoggingFreeSpec with ScalaCheckPropertyChecks {

  import TestData._

  "conversion to and from config" in {
    forAll { feature: FeatureConfig =>
      assert(FeatureConfigCompanion.read(FeatureConfigCompanion.toConfig(feature)) === Success(feature))
    }
  }

  "FeatureConfigCompanion.apply handled null values" in {
    forAll { feature: FeatureConfig =>
      assert(
        FeatureConfigCompanion.apply(
          name = feature.name,
          version = feature.version,
          url = feature.url.orNull,
          bundles = feature.bundles,
          features = feature.features
        ) === feature
      )
    }
  }

}
