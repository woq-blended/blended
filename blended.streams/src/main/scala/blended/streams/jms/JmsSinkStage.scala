package blended.streams.jms

import javax.jms.{ConnectionFactory, DeliveryMode, Message, Session}

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, StageLogging}
import akka.stream.{ActorAttributes, Attributes, Inlet, SinkShape}
import blended.jms.utils.{JMSMessageFactory, JMSSupport}
import blended.streams.message.{BinaryFlowMessage, FlowMessage, TextFlowMessage}

class JmsSinkStage(cf: ConnectionFactory) extends GraphStage[SinkShape[FlowMessage]] {

  private val in = Inlet[FlowMessage]("JmsSink.in")

  override def shape : SinkShape[FlowMessage] = SinkShape.of(in)

  override protected def initialAttributes: Attributes =
    ActorAttributes.dispatcher("akka.stream.default-blocking-io-dispatcher")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging with JMSSupport with JMSMessageFactory[FlowMessage] {

      private val logic = this

      override def createMessage(session: Session, content: FlowMessage): Message = {

        val msg = content match {
          case t : TextFlowMessage => session.createTextMessage(t.getText())
          case b : BinaryFlowMessage =>
            val r = session.createBytesMessage()
            r.writeBytes(b.getBytes().toArray)

            r
          case _ => session.createMessage()
        }

        content.header.filter{
          case (k, v) => !k.startsWith("JMS")
        }.foreach {
          case (k,v) => msg.setObjectProperty(k, v.value)
        }

        msg
      }

      override def preStart(): Unit = pull(in)

      setHandler(in,
        new InHandler {

          override def onPush(): Unit = {

            val (elem, start) = (grab(in), System.currentTimeMillis())

            sendMessage[FlowMessage](
              cf = cf,
              destName = "blended.test",
              content = elem,
              msgFactory = logic,
              deliveryMode = DeliveryMode.NON_PERSISTENT,
              priority = 4,
              ttl = 0l
            )

            log.debug(s"JMS message [$elem] sent in [${System.currentTimeMillis() - start}]")

            pull(in)
          }
        }
      )
    }
}
