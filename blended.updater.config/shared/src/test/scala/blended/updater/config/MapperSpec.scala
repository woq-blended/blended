package blended.updater.config

import java.{util => ju}

import org.scalacheck.Arbitrary
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.reflect.{ClassTag, classTag}
import scala.util.{Success, Try}

class MapperSpec extends AnyFreeSpec with ScalaCheckPropertyChecks {

  "Mapper maps and unmaps to identity" - {

    import blended.updater.config.Mapper._
    import TestData._

    def testMapping[T : ClassTag](map : T => ju.Map[String, AnyRef], unmap : AnyRef => Try[T])(implicit arb : Arbitrary[T]) : Unit = {
      classTag[T].runtimeClass.getSimpleName in {
        forAll { d : T =>
          assert(unmap(map(d)) === Success(d))
        }
      }
    }

    testMapping(mapArtifact, unmapArtifact)
    testMapping(mapBundleConfig, unmapBundleConfig)
    testMapping(mapFeatureRef, unmapFeatureRef)
    testMapping(mapFeatureConfig, unmapFeatureConfig)
    testMapping(mapOverlayConfig, unmapOverlayConfig)
    testMapping(mapRuntimeConfig, unmapRuntimeConfig)
    testMapping(mapServiceInfo, unmapServiceInfo)
    testMapping(mapUpdateAction, unmapUpdateAction)
    testMapping(mapGeneratedConfig, unmapGeneratedConfig)
    testMapping(mapProfileGroup, unmapProfileGroup)
    testMapping(mapProfile, unmapProfile)
    testMapping(mapOverlayRef, unmapOverlayRef)
    testMapping(mapOverlaySet, unmapOverlaySet)

    // FIXME: those 2 tests never return
    // testMapping(mapContainerInfo, unmapContainerInfo)
    // testMapping(mapRemoteContainerState, unmapRemoteContainerState)
  }

}
