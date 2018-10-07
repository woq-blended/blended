package blended.streams.jms

import akka.stream._
import akka.stream.stage.OutHandler
import blended.jms.utils.JmsConsumerSession
import blended.streams.message.FlowEnvelope

import scala.collection.mutable

// Common logic for the Source Stages with Auto Acknowledge and Client Acknowledge
abstract class SourceStageLogic[S <: JmsConsumerSession](
  shape: SourceShape[FlowEnvelope],
  out: Outlet[FlowEnvelope],
  settings: JMSConsumerSettings,
  inheritedAttributes: Attributes
) extends JmsStageLogic[S, JMSConsumerSettings](settings, inheritedAttributes, shape) {

  override protected def jmsSettings: JMSConsumerSettings = settings

  // A buffer holding the current inFlight messages
  private val queue = mutable.Queue[FlowEnvelope]()


  override private[jms] def doMarkStopped: Unit = {
    super.doMarkStopped
    if (queue.isEmpty) completeStage()
  }

  // This will be invoked from the JMS onMessage method
  private[jms] val handleMessage = getAsyncCallback[FlowEnvelope] { msg =>
    if (isAvailable(out)) {
      if (queue.isEmpty) {
        pushMessage(msg)
      } else {
        pushMessage(queue.dequeue())
        queue.enqueue(msg)
      }
    } else {
      queue.enqueue(msg)
    }
  }

  protected def pushMessage(msg: FlowEnvelope): Unit

  // We will process messages from the buffer if we have any
  setHandler(out, new OutHandler {
    override def onPull(): Unit = {
      if (queue.nonEmpty) {
        pushMessage(queue.dequeue())
      }

      // if we are already stopped and have finally delivered all messages,
      // we will terminate gracefully
      if (stopped.get() && queue.isEmpty) {
        completeStage()
      }
    }
  })

  override def postStop(): Unit = {
    queue.clear()
    super.stopSessions()
  }
}
