package blended.activemq.client.internal

import java.io.File

import blended.activemq.client.{ConnectionVerifier, ConnectionVerifierFactory, VerificationFailedHandler}
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.utils.IdAwareConnectionFactory
import blended.testsupport.pojosr.{MandatoryServiceUnavailable, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import domino.DominoActivator
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}

@RequiresForkedJVM
class FailingClientActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "failing").getAbsolutePath()

  var failed : List[String] = List.empty

  private class FailingActivator extends DominoActivator {

    private val failFactory : ConnectionVerifierFactory = () => new ConnectionVerifier {
      override def verifyConnection(ctCtxt: ContainerContext)(cf: IdAwareConnectionFactory)(implicit eCtxt: ExecutionContext): Future[Boolean] = Future {
        false
      }
    }

    private val failHandler : VerificationFailedHandler = (cf: IdAwareConnectionFactory) => {
      val id = s"${cf.vendor}:${cf.provider}"
      println(id)
      failed = (id :: failed).distinct
    }

    whenBundleActive {
      failFactory.providesService[ConnectionVerifierFactory]("name" -> "failing")
      failHandler.providesService[VerificationFailedHandler]("name" -> "failing")
    }
  }

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "failing" -> new FailingActivator,
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.client" -> new AmqClientActivator()
  )

  "The ActiveMQ Client Activator should" - {

    "reject to create a Connection Factory if the connection verification failed" in logException {
      mandatoryService[ContainerContext](registry)
      intercept[MandatoryServiceUnavailable](mandatoryService[IdAwareConnectionFactory](registry, filter = Some("(&(vendor=activemq)(provider=conn1))")))
      intercept[MandatoryServiceUnavailable](mandatoryService[IdAwareConnectionFactory](registry, filter = Some("(&(vendor=activemq)(provider=conn2))")))

      failed should have size 2
    }
  }
}
