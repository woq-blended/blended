package blended.streams.testsupport

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{KillSwitch, KillSwitches, Materializer, SystemMaterializer}
import blended.streams.StreamFactories
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.{Appender, AppenderBase}
import org.slf4j.LoggerFactory


class LoggingEventAppender[T](system : ActorSystem)(loggerName : String) {

  private val root : ch.qos.logback.classic.Logger = LoggerFactory.getLogger(loggerName).asInstanceOf[ch.qos.logback.classic.Logger]
  private implicit val materializer : Materializer = SystemMaterializer.get(system).materializer
  private val bufferSize = 100
  private var appender : Option[Appender[ILoggingEvent]] = None
  private var killSwitch : Option[KillSwitch] = None

  val started : AtomicBoolean = new AtomicBoolean(false)

  private class ActorLoggingAppender(actor : ActorRef) extends AppenderBase[ILoggingEvent] {
    override def append(eventObject : ILoggingEvent) : Unit = actor ! eventObject
  }

  def attachAndStart(sink : Sink[ILoggingEvent, T]) : T = {

    val ((actor, ks), result) = StreamFactories.actorSource[ILoggingEvent](bufferSize)
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(sink)(Keep.both)
      .run()

    appender = Some(new ActorLoggingAppender(actor))
    appender.foreach { a =>
      a.start()
      root.addAppender(a)
      started.set(true)
    }

    killSwitch = Some(ks)
    result
  }

  def stop() : Unit = {
    appender.foreach { a =>
      root.detachAppender(a)
      a.stop()
    }

    appender = None

    killSwitch.foreach(_.shutdown())
    killSwitch = None
  }
}
