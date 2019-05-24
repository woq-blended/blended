package blended.streams.jms

import java.util.concurrent.atomic.AtomicBoolean

import akka.Done
import akka.stream._
import akka.stream.stage.{AsyncCallback, TimerGraphStageLogic}
import blended.jms.utils.JmsSession

import scala.concurrent.Future
import scala.util.Success
import scala.util.control.NonFatal

// Common logic for the Source Stages with Auto Acknowledge and Client Acknowledge
abstract class JmsStageLogic[S <: JmsSession, T <: JmsSettings](
  settings: T,
  inheritedAttributes: Attributes,
  shape : Shape
) extends TimerGraphStageLogic(shape)
  with JmsConnector[S] {

  override protected def jmsSettings: T = settings

  // Is the Source currently stopping ?
  private[jms] val stopping = new AtomicBoolean(false)

  // Is the source stopped ?
  private[jms] var stopped = new AtomicBoolean(false)

  private[jms] def doMarkStopped = stopped.set(true)

  // Mark the source as stopped and try to finish handling all in flight messages
  private[jms] val markStopped = getAsyncCallback[Done.type] { _ => doMarkStopped }

  // Mark the source as failed and abort all message processing
  private[jms] val markAborted = getAsyncCallback[Throwable] { ex =>
    stopped.set(true)
    failStage(ex)
  }

  // async callback, so that downstream flow elements can signal an error
  private[jms] val handleError : AsyncCallback[Throwable] = getAsyncCallback[Throwable]{ t =>
    settings.log.error(t)(s"Failing stage [$id] : [${t.getMessage()}]")
    failStage(t)
  }

  // Start the configured sessions
  override def preStart(): Unit = {
    settings.log.info(s"Starting JMS Stage [$id] with [$jmsSettings]")

    materializer match {
      case am : ActorMaterializer =>
        system = am.system
        ec = system.dispatcher
      case _ =>
        failStage(new Exception(s"Expected to run on top of an ActorSystem [$id]"))
    }
    initSessionAsync()
  }

  // Asynchronously close all sessions created on behalf of this Source stage
  // TODO: For the special case of using a BlendedSingleConnectionFactory, handle the ExceptionListener correctly
  private[jms] def stopSessions(): Unit =
    if (stopping.compareAndSet(false, true)) {
      val closeSessionFutures = jmsSessions.values.map { s =>
        val f = s.closeSessionAsync()(system)
        f.failed.foreach(e => settings.log.error(e)(s"Error closing jms session in JMS source stage [$id]"))
        f
      }
      Future
        .sequence(closeSessionFutures)
        .onComplete { _ =>
          Option(jmsConnection).map{ jc =>
            jc.onComplete {
              case Success(connection) =>
                try {
                  connection.close()
                } catch {
                  case NonFatal(e) => settings.log.error(e)(s"Error closing JMS connection in Jms source stage [$id]")
                } finally {
                  // By this time, after stopping the connection, closing sessions, all async message submissions to this
                  // stage should have been invoked. We invoke markStopped as the last item so it gets delivered after
                  // all JMS messages are delivered. This will allow the stage to complete after all pending messages
                  // are delivered, thus preventing message loss due to premature stage completion.
                  markStopped.invoke(Done)
                  settings.log.debug(s"Successfully closed all sessions for Jms stage [$id][$settings]")
                }
              case _ =>
            }
          }
        }
    }

  private[jms] def abortSessions(ex: Throwable): Unit =
    if (stopping.compareAndSet(false, true)) {
      val abortSessionFutures = jmsSessions.values.map { s =>
        val f = s.abortSessionAsync()
        f.failed.foreach(e => settings.log.error(e)(s"Error closing jms session in Jms source stage [$id][$settings]"))
        f
      }
      Future
        .sequence(abortSessionFutures)
        .onComplete { _ =>
          jmsConnection.foreach { connection =>
            try {
              connection.close()
            } catch {
              case NonFatal(e) => settings.log.error(e)(s"Error closing JMS connection in Jms source stage [$id]")
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
    stopSessions()
  }
}

