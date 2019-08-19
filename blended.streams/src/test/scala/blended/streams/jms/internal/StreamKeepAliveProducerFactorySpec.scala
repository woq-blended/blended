package blended.streams.jms.internal

import java.io.File

import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.Matchers
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{BlendedSingleConnectionFactory, IdAwareConnectionFactory, MessageReceived, ProducerMaterialized}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestProbe
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.streams.BlendedStreamsConfig
import blended.streams.internal.BlendedStreamsConfigImpl
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.BlendedTestSupport
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator

import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class StreamKeepAliveProducerFactorySpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "keepAlive").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

  implicit private val timeout : FiniteDuration = 1.second
  implicit private val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  implicit private val materializer : Materializer = ActorMaterializer()
  implicit private val eCtxt : ExecutionContext = system.dispatcher

  private val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](registry)(None)
  private val cf : BlendedSingleConnectionFactory = mandatoryService[IdAwareConnectionFactory](registry)(None).asInstanceOf[BlendedSingleConnectionFactory]

  private val streamCfg : BlendedStreamsConfig = BlendedStreamsConfigImpl(idSvc)

  "The stream based keep alive producer should" - {

    "create a stream to send keep alives for a given connection factory" in {

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[MessageReceived])
      system.eventStream.subscribe(probe.ref, classOf[ProducerMaterialized])

      val factory : KeepAliveProducerFactory = new StreamKeepAliveProducerFactory(
        log = bcf => Logger(s"blended.streams.jms.keepalive.${bcf.vendor}.${bcf.provider}"),
        idSvc = idSvc,
        streamsCfg = streamCfg
      )

      factory.start(cf)

      val p : Promise[MessageReceived] = Promise[MessageReceived]()

      val pm = probe.fishForMessage(3.seconds){
        case pm : ProducerMaterialized => true
        case _ => false
      }.asInstanceOf[ProducerMaterialized]

      val env : FlowEnvelope = FlowEnvelope()
      pm.prod ! env
      p.complete(Success(probe.expectMsgType[MessageReceived]))

      Await.result(p.future, 3.seconds)
    }
  }
}
