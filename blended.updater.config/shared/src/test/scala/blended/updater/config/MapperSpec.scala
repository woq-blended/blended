package blended.updater.config

import java.{util => ju}

import scala.reflect.{ClassTag, classTag}
import scala.util.{Success, Try}

import org.scalacheck.Arbitrary
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class MapperSpec extends AnyFreeSpec with ScalaCheckPropertyChecks {

  "Mapper maps and unmaps to identity" - {

    import TestData._
    import blended.updater.config.Mapper._

    def testMapping[T: ClassTag](map: T => ju.Map[String, AnyRef],
                                 unmap: AnyRef => Try[T])(implicit arb: Arbitrary[T]): Unit = {
      classTag[T].runtimeClass.getSimpleName in {
        forAll { d: T =>
          assert(unmap(map(d)) === Success(d))
        }
      }
    }

    testMapping(mapArtifact, unmapArtifact)
    testMapping(mapBundleConfig, unmapBundleConfig)
    testMapping(mapFeatureRef, unmapFeatureRef)
    testMapping(mapFeatureConfig, unmapFeatureConfig)
    testMapping(mapProfile, unmapProfile)
    testMapping(mapServiceInfo, unmapServiceInfo)
    testMapping(mapGeneratedConfig, unmapGeneratedConfig)
    testMapping(mapProfileRef, unmapProfileRef)

    // FIXME: those 2 tests never return
    // testMapping(mapContainerInfo, unmapContainerInfo)
    // testMapping(mapRemoteContainerState, unmapRemoteContainerState)
  }

}
