package blended.updater.config

import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class ArtifactSpec extends AnyFreeSpec with ScalaCheckPropertyChecks {

  import TestData._

  "Auxiliary Artifact.apply should support null-values" in  {
    forAll { a: Artifact =>
      assert(Artifact(a.url, a.fileName.orNull, a.sha1Sum.orNull) === a)
    }
  }
}
