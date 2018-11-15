package blended.updater.config

import java.{util => ju}

import scala.reflect.{ClassTag, classTag}
import scala.util.{Success, Try}

import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalacheck.Arbitrary
import org.scalactic.anyvals.PosInt
import org.scalatest.prop.PropertyChecks

class MapperSpec extends LoggingFreeSpec with PropertyChecks {

  "Mapper maps and unmaps to identity" - {

    import Mapper._
    import TestData._

    implicit val generatorDrivenConfig = PropertyCheckConfiguration(
      workers = PosInt.from(Runtime.getRuntime().availableProcessors()).get
    )

    val log = Logger[this.type]

    def testMapping[T: ClassTag](map: T => ju.Map[String, AnyRef], unmap: AnyRef => Try[T])(implicit arb: Arbitrary[T]): Unit = {
      classTag[T].runtimeClass.getSimpleName in {
        forAll { d: T =>
          //          log.info(s"Mapping [${d}]")
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
    testMapping(mapProfile, unmapProfile)
    testMapping(mapOverlayRef, unmapOverlayRef)
    testMapping(mapOverlaySet, unmapOverlaySet)

    // FIXME: those 2 tests never return
    //    testMapping(mapContainerInfo, unmapContainerInfo)
    //    testMapping(mapRemoteContainerState, unmapRemoteContainerState)
  }

}
