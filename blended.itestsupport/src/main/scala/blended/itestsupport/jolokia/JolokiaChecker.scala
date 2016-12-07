package blended.itestsupport.jolokia

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import blended.itestsupport.condition.{AsyncChecker, AsyncCondition}
import blended.jolokia.{JolokiaAddress, JolokiaClient}

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
