package blended.streams.jms.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestProbe
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.utils.{BlendedSingleConnectionFactory, IdAwareConnectionFactory, MessageReceived, ProducerMaterialized}
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{EnsureJmsConnectivity, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers
import blended.util.RichTry._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.util.Success

class StreamKeepAliveProducerFactorySpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with EnsureJmsConnectivity
  with Matchers {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "keepAlive").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

  implicit private val timeout : FiniteDuration = 3.seconds
  implicit private val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  implicit private val materializer : Materializer = ActorMaterializer()
  implicit private val eCtxt : ExecutionContext = system.dispatcher

  private val ctCtxt : ContainerContext = mandatoryService[ContainerContext](registry)(None)
  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(ctCtxt)
  private val cf : BlendedSingleConnectionFactory = mandatoryService[IdAwareConnectionFactory](registry)(None).asInstanceOf[BlendedSingleConnectionFactory]

  ensureConnection(cf, timeout).unwrap

  private val streamCfg : BlendedStreamsConfig = BlendedStreamsConfig.create(ctCtxt)

  "The stream based keep alive producer should" - {

    "create a stream to send keep alives for a given connection factory" in {

      val probe : TestProbe = TestProbe()
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
