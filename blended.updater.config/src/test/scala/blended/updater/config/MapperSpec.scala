package blended.updater.config

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.{util => ju}
import scala.reflect.{ClassTag, classTag}
import scala.util.{Failure, Success, Try}

class MapperSpec extends AnyFreeSpec with ScalaCheckPropertyChecks {

  "Mapper maps and unmaps to identity" - {

    import TestData._
    import blended.updater.config.Mapper._

    def testMapping[T: ClassTag](
      g : Gen[T],
      map: T => ju.Map[String, AnyRef],
      unmap: AnyRef => Try[T],
      withOutput : Boolean = false
    ): Unit = {
      classTag[T].runtimeClass.getSimpleName in {
        forAll(g) { d =>
          if (withOutput) {
            println(d)
          }
          unmap(map(d)) match {
            case Failure(exception) => throw(exception)
            case Success(s) => s === d
          }
        }
      }
    }

    testMapping(artifacts, mapArtifact, unmapArtifact)
    testMapping(bundleConfigs, mapBundleConfig, unmapBundleConfig)
    testMapping(featureRefs, mapFeatureRef, unmapFeatureRef)
    testMapping(featureConfigs, mapFeatureConfig, unmapFeatureConfig)
    testMapping(profiles, mapProfile, unmapProfile)
    testMapping(serviceInfos, mapServiceInfo, unmapServiceInfo)
    testMapping(generatedConfigs, mapGeneratedConfig, unmapGeneratedConfig)
    testMapping(profileRefs, mapProfileRef, unmapProfileRef)

    // FIXME: those 2 tests never return
    testMapping(containerInfos, mapContainerInfo, unmapContainerInfo)
    testMapping(remoteContainerStates, mapRemoteContainerState, unmapRemoteContainerState)
  }

}
