package blended.streams.jms

import java.util.concurrent.atomic.AtomicBoolean

import akka.Done
import akka.stream.stage.{GraphStageLogic, OutHandler, StageLogging}
import akka.stream.{Attributes, KillSwitch, Outlet, SourceShape}
import blended.streams.message.FlowMessage

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.NonFatal

abstract class SourceStageLogic(
  shape: SourceShape[FlowMessage],
  out: Outlet[FlowMessage],
  settings: JMSConsumerSettings,
  inheritedAttributes: Attributes
) extends GraphStageLogic(shape)
  with JmsConsumerConnector
  with StageLogging {

  override protected def jmsSettings: JMSConsumerSettings = settings

  private val queue = mutable.Queue[FlowMessage]()
  private val stopping = new AtomicBoolean(false)
  private var stopped = false

  private val markStopped = getAsyncCallback[Done.type] { _ =>
    stopped = true
    if (queue.isEmpty) completeStage()
  }

  private val markAborted = getAsyncCallback[Throwable] { ex =>
    stopped = true
    failStage(ex)
  }

  private[jms] val handleError = getAsyncCallback[Throwable] { e =>
    fail(out, e)
  }

  override def preStart(): Unit = {
    log.info("--->>> Starting JMS Source Stage")
    ec = executionContext(inheritedAttributes)
    initSessionAsync(false)
  }

  private[jms] val handleMessage = getAsyncCallback[FlowMessage] { msg =>
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

  protected def pushMessage(msg: FlowMessage): Unit

  setHandler(out, new OutHandler {
    override def onPull(): Unit = {
      if (queue.nonEmpty) pushMessage(queue.dequeue())
      if (stopped && queue.isEmpty) completeStage()
    }
  })

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
              // By this time, after stopping connection, closing sessions, all async message submissions to this
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
              log.info("JMS connection {} closed", jmsConnection)
            } catch {
              case NonFatal(e) => log.error(e, "Error closing JMS connection {}", jmsConnection)
            } finally {
              markAborted.invoke(ex)
            }
          }
        }
    }

  private[jms] def killSwitch = new KillSwitch {
    override def shutdown(): Unit = stopSessions()
    override def abort(ex: Throwable): Unit = abortSessions(ex)
  }

  override def postStop(): Unit = {
    log.info("Stopping Jms Source")
    queue.clear()
    stopSessions()
  }
}
