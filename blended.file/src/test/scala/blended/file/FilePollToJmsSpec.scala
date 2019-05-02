package blended.file

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import blended.jms.utils.{JmsDestination, JmsQueue}
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.transaction.FlowHeaderConfig
import blended.testsupport.RequiresForkedJVM
import blended.testsupport.scalatest.LoggingFreeSpecLike
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

  private val dest : JmsDestination = JmsQueue("filepoll")

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
      jmsDestination = Some(dest)
    )

    val jmsHandler = new JMSFilePollHandler(
      settings = settings,
      header = FlowMessage.props("ResourceType" -> "myType").get,
      bufferSize = FilePollActor.batchSize
    )

    jmsHandler.start()

    jmsHandler
  }

  private def withJmsMessages(dir : String, msgCount : Int)(implicit timeout : FiniteDuration) : Unit = {

    val files : List[File] = withMessages(dir, msgCount)(defaultTest)

    val collector : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = FlowHeaderConfig.create("App"),
      cf = connF,
      dest = dest,
      log = log,
      listener = 5
    )

    val msgs : List[FlowEnvelope] = Await.result(collector.result, timeout + 500.millis)
    msgs should have size(files.size)
  }

  "The JMS File Poller should" - {

    "do perform a regular poll and process files" in {
      withJmsMessages("jmspoll", 5)(5.seconds)
    }

    "do perform a regular poll and process files (bulk)" in {
      withJmsMessages("jmsbulk", 2000)(10.seconds)
    }
  }
}
