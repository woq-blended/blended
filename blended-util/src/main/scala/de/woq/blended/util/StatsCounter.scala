package de.woq.blended.util

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import de.woq.blended.util.protocol._

class StatsCounter extends Actor with ActorLogging {

  var count = 0
  var firstCount : Option[Long] = None
  var lastCount  : Option[Long] = None

  override def receive = LoggingReceive {
    case IncrementCounter(c) => {
      firstCount match {
        case None => {
          firstCount = Some(System.currentTimeMillis)
          lastCount = firstCount
        }
        case _ => lastCount = Some(System.currentTimeMillis)
      }
      count += c
    }
    case QueryCounter => sender ! new CounterInfo(
      count,
      firstCount,
      lastCount
    )
  }
}
