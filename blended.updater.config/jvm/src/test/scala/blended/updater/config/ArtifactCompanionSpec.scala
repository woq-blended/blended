package blended.updater.config

import scala.util.Success

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class ArtifactCompanionSpec extends LoggingFreeSpec with ScalaCheckPropertyChecks {

  import TestData._

  "conversion to and from config" in {
    forAll { artifact: Artifact =>
      assert(ArtifactCompanion.read(ArtifactCompanion.toConfig(artifact)) === Success(artifact))
    }
  }

}
