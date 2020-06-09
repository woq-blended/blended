package blended.persistence.jdbc

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.{util => ju}

import scala.jdk.CollectionConverters._
import scala.reflect.{ClassTag, classTag}
import scala.util.Try

import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalacheck.Arbitrary
import org.scalatest.DoNotDiscover
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

@DoNotDiscover
class LoadTest extends LoggingFreeSpec with ScalaCheckPropertyChecks with DbFactory {

  private[this] val log = Logger[this.type]

  "Persisting and loading arbitrary instances of 'blended.updater.config' classes" - {

    import blended.updater.config.TestData._

    def testMapping[T: ClassTag](
        map: T => ju.Map[String, AnyRef],
        unmap: AnyRef => Try[T]
    )(implicit arb: Arbitrary[T]): Unit = {
      val className = classTag[T].runtimeClass.getSimpleName
      className in logException {
        withTestPersistenceService() { ctx =>
          val startTime = System.currentTimeMillis()
          val count = new AtomicInteger()
          try {
            forAll { d: T =>
              count.incrementAndGet()
              val uuid = UUID.randomUUID().toString()
              val idCol = "TEST@ID@"
              val data = new ju.HashMap[String, AnyRef](map(d))
              data.put(idCol, uuid)
              //              log.info(s"Persisting [${data}] with special field [${idCol}] [${uuid}]")
              //              val time = System.currentTimeMillis()
              ctx.persistenceService.persist(className, data)
              val loaded = ctx.persistenceService.findByExample(className, Map(idCol -> uuid).asJava)
              //              log.info("Now loading...")
              val loadedData = loaded.map(unmap).map(_.get)
              //              log.info(s"Loaded data [${loadedData}] took [${System.currentTimeMillis() - time}] ms")
              assert(loadedData === Seq(d))
            }
          } finally {

            val endTime = System.currentTimeMillis()
            log.info(
              s"Persisting [${count.get()}] [${className}] entries took [${endTime - startTime}] ms (including time for generating and mapping data)")
          }
        }
      }
    }

    import blended.updater.config.Mapper._

    testMapping(mapArtifact, unmapArtifact)
    testMapping(mapBundleConfig, unmapBundleConfig)
    testMapping(mapFeatureRef, unmapFeatureRef)
    testMapping(mapFeatureConfig, unmapFeatureConfig)
    testMapping(mapProfile, unmapProfile)
    testMapping(mapServiceInfo, unmapServiceInfo)
    testMapping(mapUpdateAction, unmapUpdateAction)
    testMapping(mapGeneratedConfig, unmapGeneratedConfig)
    testMapping(mapProfileRef, unmapProfileRef)

  }

}
