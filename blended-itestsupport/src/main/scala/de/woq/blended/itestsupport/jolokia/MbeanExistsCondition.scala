package de.woq.blended.itestsupport.jolokia

import akka.actor.ActorSystem
import de.woq.blended.jolokia.model.JolokiaSearchResult
import de.woq.blended.jolokia.protocol._

import scala.concurrent.duration.FiniteDuration

class MbeanExistsCondition(
  url: String,
  timeout: FiniteDuration,
  userName: Option[String] = None,
  userPwd: Option[String] = None
)(implicit system:ActorSystem) extends JolokiaCondition(url, timeout, userName, userPwd) with JolokiaAssertion {
  this: MBeanSearchSpec =>

  override def toString = s"MbeanExistsCondition(${url}, ${pattern}})"

  override def jolokiaRequest = SearchJolokia(this)

  override def assertJolokia = { msg =>
    msg match {
      case v : JolokiaSearchResult => !v.mbeanNames.isEmpty
      case _ => false
    }
  }
}
