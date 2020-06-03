package blended.streams

import akka.actor.{Actor, Props}
import akka.stream.scaladsl.Source

object StreamController {

  case object Start
  case object Stop
  case class Abort(t : Throwable)
  case class StreamTerminated(exception : Option[Throwable])
  case object Reset

  def props[T, Mat](
    streamName : String,
    src : Source[T, Mat],
    streamCfg : BlendedStreamsConfig
  )(
    onMaterialize : Mat => Unit = { _ : Mat => () }
  ) : Props =

    Props(new AbstractStreamController[T, Mat](streamCfg) {
      override def name: String = streamName
      override def source() : Source[T, Mat] = src
      override def materialized(m: Mat): Unit = onMaterialize(m)
    })
}

abstract class AbstractStreamController[T, Mat](streamCfg : BlendedStreamsConfig)
  extends Actor
  with StreamControllerSupport[T, Mat] {

  override def preStart() : Unit = self ! StreamController.Start

  override def receive : Receive = starting(streamCfg, streamCfg.minDelay)

  override def toString : String = s"${getClass().getSimpleName()}($streamCfg)"
}
