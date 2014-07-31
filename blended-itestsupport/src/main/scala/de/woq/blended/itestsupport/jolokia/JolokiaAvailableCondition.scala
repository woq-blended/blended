package de.woq.blended.itestsupport.jolokia

import java.util.concurrent.atomic.AtomicBoolean

import akka.pattern.ask
import akka.actor.{Props, ActorSystem}
import akka.util.Timeout
import de.woq.blended.itestsupport.condition.Condition
import de.woq.blended.jolokia.model.JolokiaVersion
import de.woq.blended.jolokia.{JolokiaAddress, JolokiaClient}

import de.woq.blended.jolokia.protocol._

import scala.util.{Try, Failure, Success}
import scala.concurrent.duration._

class JolokiaAvailableCondition(
  url: String,
  userName: Option[String] = None,
  userPwd: Option[String] = None
)(implicit system: ActorSystem) extends Condition {

  class JolokiaConnector extends JolokiaClient with JolokiaAddress {
    override val jolokiaUrl = url
    override val user       = userName
    override val password   = userPwd
  }

  val jolokiaAvailable = new AtomicBoolean(false)

  override def satisfied = {
    jolokiaAvailable.get
  }

}
