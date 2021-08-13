package blended.streams.testsupport

import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.MsgProperty

object StreamAssertions {

  def verifyHeader(
    expected: FlowMessageProps,
    actual: FlowMessageProps
  ): List[(String, MsgProperty, Option[MsgProperty])] = {

    //println(actual)

    val broken = expected.filter { p =>
      actual.get(p._1) match {
        case None     => true
        case Some(ep) => !p._2.equals(ep)
      }
    }

    broken.map {
      case (k, p) =>
        (k, p, actual.get(k))
    }.toList
  }

}
