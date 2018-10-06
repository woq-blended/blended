package blended.streams.jms

import java.util.concurrent.atomic.AtomicBoolean

import akka.Done
import akka.stream.stage.{GraphStageLogic, OutHandler, StageLogging}
import akka.stream.{Attributes, KillSwitch, Outlet, SourceShape}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.message.FlowEnvelope

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.NonFatal

// Common logic for the Source Stages with Auto Acknowledge and Client Acknowledge
abstract class SourceStageLogic(
  shape: SourceShape[FlowEnvelope],
  out: Outlet[FlowEnvelope],
  settings: JMSConsumerSettings,
  inheritedAttributes: Attributes
) extends GraphStageLogic(shape)
  with JmsConsumerConnector
  with StageLogging {

  override protected def jmsSettings: JMSConsumerSettings = settings

  // Just to identify the Source stage in log statements
  val id : String = { jmsSettings.connectionFactory match {
    case idAware: IdAwareConnectionFactory => idAware.id
    case cf => cf.toString()
  }}

  // A buffer holding the current inFlight messages
  private val queue = mutable.Queue[FlowEnvelope]()

  // Is the Source currently stopping ?
  private val stopping = new AtomicBoolean(false)

  // Is the source stopped ?
  private var stopped = false

  // Mark the source as stopped and try to finish handling all in flight messages
  private val markStopped = getAsyncCallback[Done.type] { _ =>
    stopped = true
    if (queue.isEmpty) completeStage()
  }

  // Mark the source as failed and abort all message processing
  private val markAborted = getAsyncCallback[Throwable] { ex =>
    stopped = true
    failStage(ex)
  }

  // async callback, so that downstream flow elements can signal an error
  private[jms] val handleError = getAsyncCallback[Throwable] { e =>
    fail(out, e)
  }

  // This is where the listeners are created
  override def preStart(): Unit = {
    log.info(s"Starting JMS Source Stage [$id]")
    ec = executionContext(inheritedAttributes)
    initSessionAsync(false)
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
      if (stopped && queue.isEmpty) {
        completeStage()
      }
    }
  })

  // Asynchronously close all sessions created on behalf of this Source stage
  // TODO: For the special case of using a BlendedSingleConnectionFactory, handle the ExceptionListener correctly
  private def stopSessions(): Unit =
    if (stopping.compareAndSet(false, true)) {
      val closeSessionFutures = jmsSessions.map { s =>
        val f = s.closeSessionAsync()
        f.failed.foreach(e => log.error(e, "Error closing jms session"))
        f
      }
      Future
        .sequence(closeSessionFutures)
        .onComplete { _ =>
          jmsConnection.foreach { connection =>
            try {
              connection.close()
            } catch {
              case NonFatal(e) => log.error(e, "Error closing JMS connection {}", connection)
            } finally {
              // By this time, after stopping the connection, closing sessions, all async message submissions to this
              // stage should have been invoked. We invoke markStopped as the last item so it gets delivered after
              // all JMS messages are delivered. This will allow the stage to complete after all pending messages
              // are delivered, thus preventing message loss due to premature stage completion.
              markStopped.invoke(Done)
            }
          }
        }
    }

  private def abortSessions(ex: Throwable): Unit =
    if (stopping.compareAndSet(false, true)) {
      val abortSessionFutures = jmsSessions.map { s =>
        val f = s.abortSessionAsync()
        f.failed.foreach(e => log.error(e, "Error closing jms session"))
        f
      }
      Future
        .sequence(abortSessionFutures)
        .onComplete { _ =>
          jmsConnection.foreach { connection =>
            try {
              connection.close()
            } catch {
              case NonFatal(e) => log.error(e, "Error closing JMS connection {}", id)
            } finally {
              markAborted.invoke(ex)
            }
          }
        }
    }

  // We expose the killswitch, so that the stage can be closed externally
  private[jms] def killSwitch = new KillSwitch {
    override def shutdown(): Unit = stopSessions()
    override def abort(ex: Throwable): Unit = abortSessions(ex)
  }

  override def postStop(): Unit = {
    log.info(s"Stopping Jms Source [$id]")
    queue.clear()
    stopSessions()
  }
}
