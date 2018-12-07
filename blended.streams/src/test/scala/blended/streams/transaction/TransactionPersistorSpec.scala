package blended.streams.transaction

import java.io.File

import blended.persistence.PersistenceService
import blended.persistence.h2.internal.H2Activator
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

import scala.concurrent.duration._

@RequiresForkedJVM
class TransactionPersistorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper
  with PropertyChecks  {

  System.setProperty("testName", "persistor")

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.persistence.h2" -> new H2Activator()
  )

  implicit private val timeout : FiniteDuration = 5.seconds
  private val pSvc = mandatoryService[PersistenceService](registry)(None)

  private val persistor = new FlowTransactionPersistor(pSvc)

  "The FlowTransaction Persistor should" - {

    "persist and restore a transaction" in {

      forAll(FlowTransactionGen.genTrans) { t =>

        persistor.persistTransaction(t).get

        val t2 = persistor.restoreTransaction(t.id).get

        t2.id should be (t.id)
        t2.state should be (t.state)

        t2.worklist should be  (t.worklist)
        t2.creationProps should be (t.creationProps)
      }

    }
  }
}
