package blended.file

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import blended.jms.utils.{IdAwareConnectionFactory, JmsQueue}
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.transaction.FlowHeaderConfig
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{RequiresForkedJVM, TestActorSys}
import blended.util.logging.Logger
import org.apache.activemq.broker.BrokerService
import org.scalatest.{BeforeAndAfterAll, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

@RequiresForkedJVM
class FilePollToJmsSpec extends TestKit(ActorSystem("JmsFilePoll"))
  with AbstractFilePollSpec
  with Matchers
  with LoggingFreeSpecLike
  with AmqBrokerSupport
  with BeforeAndAfterAll
  with JmsStreamSupport {

  private val log : Logger = Logger[FilePollToJmsSpec]
  private var brokerSvc : Option[BrokerService] = None

  private implicit val materializer : Materializer = ActorMaterializer()

  override protected def beforeAll(): Unit = {
    brokerSvc = Some(startBroker())
  }

  override protected def afterAll(): Unit = {
    stopBroker(brokerSvc)
    brokerSvc = None
  }

  override def handler()(implicit system: ActorSystem): FilePollHandler = {

    val settings : JmsProducerSettings = JmsProducerSettings(
      log = log,

      connectionFactory = connF,
      jmsDestination = Some(JmsQueue("filepoll"))
    )

    new JMSFilePollHandler(settings, FlowMessage.props("ResourceType" -> "myType").get)
  }

  private def withJmsMessages(dir : String, msgCount : Int)(implicit timeout : FiniteDuration) : Unit = {

    val files : List[File] = withMessages(dir, msgCount)

    val collector : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = FlowHeaderConfig.create("App"),
      cf = connF,
      dest = JmsQueue("filepoll"),
      log = log,
      listener = 1
    )

    val msgs : List[FlowEnvelope] = Await.result(collector.result, timeout + 500.millis)
    msgs should have size(files.size)
  }

  "The JMS File Poller should" - {

    "do perform a regular poll and process files" in {
      withJmsMessages("pollspec", 5)(3.seconds)
    }

    "do perform a regular poll and process files (bulk)" in {
      withJmsMessages("pollspec", 500)(10.seconds)
    }
  }

}
