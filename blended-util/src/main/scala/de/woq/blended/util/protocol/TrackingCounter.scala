/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.woq.blended.util.protocol

import akka.actor._
import akka.event.LoggingReceive
import akka.util.Timeout
import de.woq.blended.util.StatsCounter
import akka.pattern.{ask, pipe}
import scala.concurrent.duration._

object TrackingCounter {
  def apply(idleTimeout: FiniteDuration = 3.seconds, counterFor: ActorRef) =
    new TrackingCounter(idleTimeout, counterFor)
}

class TrackingCounter(idleTimeout: FiniteDuration, counterFor: ActorRef)
  extends Actor with ActorLogging {

  case object Tick

  implicit val ctxt = context.dispatcher
  implicit val timeout = Timeout(3.seconds)

  var counter : ActorRef = _
  var timer   : Option[Cancellable] = None

  override def preStart(): Unit = {
    super.preStart()
    counter = context.actorOf(Props[StatsCounter])
    resetTimer()
  }

  override def receive = LoggingReceive {
    case Tick => {
      self ! StopCounter
    }
    case ic : IncrementCounter => {
      resetTimer()
      counter.forward(ic)
    }
    case StopCounter => {
      timer.foreach(_.cancel())
      (counter ? QueryCounter).mapTo[CounterInfo].map { info =>
        counterFor ! info
        context.stop(self)
      }
    }
    case QueryCounter => counter.forward()
  }

  private def resetTimer(): Unit = {
    timer.foreach(_.cancel())
    timer = Some(context.system.scheduler.scheduleOnce(idleTimeout, self, Tick))
  }
}
