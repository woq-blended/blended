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

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import de.woq.blended.itestsupport.condition.{AsyncChecker, AsyncCondition}
import de.woq.blended.jolokia.{JolokiaAddress, JolokiaClient}

trait JolokiaAssertion {
  def jolokiaRequest : Any
  def assertJolokia  : Any => Boolean
}

class JolokiaChecker(url: String, userName: Option[String], password: Option[String]) extends AsyncChecker {
  this: JolokiaAssertion =>

  var jolokiaConnector : Option[ActorRef] = None

  object JolokiaConnector {
    def apply(url: String, userName: Option[String], userPwd: Option[String]) =
      new JolokiaClient with JolokiaAddress {
        override val jolokiaUrl = url
        override val user       = userName
        override val password   = userPwd
      }
  }

  override def preStart() : Unit = {
    jolokiaConnector = Some(context.actorOf(Props(JolokiaConnector(url, userName, password))))
  }

  override def performCheck(condition: AsyncCondition) = {
    implicit val t = Timeout(condition.timeout)
    (jolokiaConnector.get ? jolokiaRequest).map { result =>
      assertJolokia(result)
    }
  }
}
