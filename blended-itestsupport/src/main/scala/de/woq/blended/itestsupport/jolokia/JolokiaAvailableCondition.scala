package de.woq.blended.itestsupport.jolokia

import akka.actor.ActorSystem
import de.woq.blended.jolokia.model.JolokiaVersion
import de.woq.blended.jolokia.protocol._

import scala.concurrent.duration.FiniteDuration

class JolokiaAvailableCondition(
  url: String,
  timeout: FiniteDuration,
  userName: Option[String] = None,
  userPwd: Option[String] = None
)(implicit system:ActorSystem) extends JolokiaCondition(url, timeout, userName, userPwd) with JolokiaAssertion {

  override def jolokiaRequest = GetJolokiaVersion

  override def assertJolokia = { msg =>
    msg match {
      case v : JolokiaVersion => true
      case _ => false
    }
  }
}
