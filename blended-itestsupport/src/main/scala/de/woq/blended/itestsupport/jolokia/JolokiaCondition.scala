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

package de.woq.blended.itestsupport.jolokia

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import de.woq.blended.itestsupport.condition.{Condition, ConditionChecker}
import de.woq.blended.itestsupport.protocol._
import de.woq.blended.jolokia.{JolokiaAddress, JolokiaClient}

import scala.concurrent.duration.FiniteDuration

trait JolokiaAssertion {
  def jolokiaRequest : Any
  def assertJolokia  : Any => Boolean
}

abstract class JolokiaCondition (
  url: String,
  jolokiaTimeout: FiniteDuration,
  userName: Option[String] = None,
  userPwd: Option[String] = None
)(implicit system: ActorSystem) extends Condition { this : JolokiaAssertion =>

  object JolokiaConnector {
    def apply(url: String, userName: Option[String], userPwd: Option[String]) =
      new JolokiaClient with JolokiaAddress {
        override val jolokiaUrl = url
        override val user       = userName
        override val password   = userPwd
      }
  }

  override def timeout = jolokiaTimeout

  implicit val eCtxt = system.dispatcher

  val jolokiaAsserted  = new AtomicBoolean(false)
  val connector        = system.actorOf(Props(JolokiaConnector(url, userName, userPwd)))
  val checker          = system.actorOf(Props(ConditionChecker(this, Props(JolokiaChecker(this, connector)))))

  (checker ? CheckCondition)(timeout).mapTo[ConditionSatisfied].map {
    _ => jolokiaAsserted.set(true)
  }.andThen {
    case _ => Seq(connector, checker).foreach { system.stop(_) }
  }

  override def satisfied = jolokiaAsserted.get

  object JolokiaChecker {
    def apply(condition: Condition, connector: ActorRef) = new JolokiaChecker(condition, connector)
  }

  class JolokiaChecker(condition: Condition, connector: ActorRef) extends Actor with ActorLogging {

    def receive = idle

    def idle : Receive = LoggingReceive {
      case CheckCondition => {
        context become(busy(sender))
        connector ! jolokiaRequest
      }
    }

    def busy(requestor: ActorRef) : Receive = LoggingReceive {
      case msg  => {
        if (assertJolokia(msg)) {
          requestor ! ConditionCheckResult(condition, true)
          context.stop(self)
        } else {
          context.become(idle)
        }
      }
    }
  }
}
