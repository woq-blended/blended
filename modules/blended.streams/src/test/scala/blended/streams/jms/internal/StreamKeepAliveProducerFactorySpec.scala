package blended.streams.jms.internal

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.{BlendedSingleConnectionFactory, MessageReceived, ProducerMaterialized}
import blended.streams.BlendedStreamsConfig
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{JmsConnectionHelper, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.Success

class StreamKeepAliveProducerFactorySpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with JmsConnectionHelper
  with Matchers {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "keepAlive").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

//

  "The stream based keep alive producer should" - {

    "create a stream to send keep alives for a given connection factory" in {
      implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

      val cf : BlendedSingleConnectionFactory = jmsConnectionFactory(registry, mustConnect = true).get.asInstanceOf[BlendedSingleConnectionFactory]

      val streamCfg : BlendedStreamsConfig = BlendedStreamsConfig.create(ctCtxt)

      val probe : TestProbe = TestProbe()(system)
      system.eventStream.subscribe(probe.ref, classOf[MessageReceived])
      system.eventStream.subscribe(probe.ref, classOf[ProducerMaterialized])

      val factory : KeepAliveProducerFactory = new StreamKeepAliveProducerFactory(
        log = bcf => FlowEnvelopeLogger.create(headerCfg, Logger(s"blended.streams.jms.keepalive.${bcf.vendor}.${bcf.provider}")),
        ctCtxt = ctCtxt,
        streamsCfg = streamCfg
      )

      factory.start(cf)

      val p : Promise[MessageReceived] = Promise[MessageReceived]()

      val pm = probe.fishForMessage(timeout){
        case _ : ProducerMaterialized => true
        case _ => false
      }.asInstanceOf[ProducerMaterialized]

      val env : FlowEnvelope = FlowEnvelope()
      pm.prod ! env
      p.complete(Success(probe.expectMsgType[MessageReceived]))

      Await.result(p.future, 3.seconds)
    }
  }
}
