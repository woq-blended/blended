package blended.streams.testsupport

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitch, KillSwitches, Materializer, OverflowStrategy}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.{Appender, AppenderBase}
import org.slf4j.LoggerFactory

class LoggingEventAppender[T](system : ActorSystem)(loggerName : String) {

  implicit val materializer : Materializer = ActorMaterializer()(system)

  val root = LoggerFactory.getLogger(loggerName).asInstanceOf[ch.qos.logback.classic.Logger]

  private val bufferSize = 100
  private var appender : Option[Appender[ILoggingEvent]] = None
  private var killSwitch : Option[KillSwitch] = None

  private class ActorLoggingAppender(actor : ActorRef) extends AppenderBase[ILoggingEvent] {
    override def append(eventObject : ILoggingEvent) : Unit = actor ! eventObject
  }

  def attachAndStart(sink : Sink[ILoggingEvent, T]) : T = {

    val ((actor, ks), result) = Source.actorRef[ILoggingEvent](bufferSize, OverflowStrategy.fail)
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(sink)(Keep.both)
      .run()

    appender = Some(new ActorLoggingAppender(actor))
    appender.foreach { a =>
      a.start()
      root.addAppender(a)
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
