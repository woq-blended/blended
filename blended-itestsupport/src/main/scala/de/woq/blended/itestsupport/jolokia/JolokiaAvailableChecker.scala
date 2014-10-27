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

import akka.actor.{Props, ActorSystem}
import de.woq.blended.itestsupport.condition.AsyncCondition
import de.woq.blended.jolokia.model.JolokiaVersion
import de.woq.blended.jolokia.protocol._

import scala.concurrent.duration.FiniteDuration

object JolokiaAvailableCondition {
  def apply(
    url: String,
    t: Option[FiniteDuration] = None,
    user: Option[String] = None,
    pwd: Option[String] = None
  )(implicit actorSys: ActorSystem) =
    AsyncCondition(Props(JolokiaAvailableChecker(url, user, pwd)), s"JolokiaAvailableCondition(${url})", t)
}

private[jolokia] object JolokiaAvailableChecker {
  def apply(
    url: String,
    userName: Option[String] = None,
    userPwd: Option[String] = None
  )(implicit actorSys: ActorSystem) = new JolokiaAvailableChecker(url, userName, userPwd)
}

private[jolokia] class JolokiaAvailableChecker(
  url: String,
  userName: Option[String] = None,
  userPwd: Option[String] = None
)(implicit system:ActorSystem) extends JolokiaChecker(url, userName, userPwd) with JolokiaAssertion {

  override def toString = s"JolokiaAvailableCondition(${url})"

  override def jolokiaRequest = GetJolokiaVersion

  override def assertJolokia = { msg =>
    msg match {
      case v : JolokiaVersion => true
      case _ => false
    }
  }
}
