package blended.itestsupport.jolokia

import akka.actor.{ActorSystem, Props}
import blended.itestsupport.condition.AsyncCondition
import blended.jolokia.{JolokiaClient, JolokiaObject, JolokiaVersion}
import blended.util.logging.Logger

import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}

object JolokiaAvailableCondition {
  def apply(
    client : JolokiaClient,
    t: Option[FiniteDuration] = None,
  )(implicit actorSys: ActorSystem) =
    AsyncCondition(Props(JolokiaAvailableChecker(client)), s"JolokiaAvailableCondition(${client.url})", t)
}

private[jolokia] object JolokiaAvailableChecker {
  def apply(
    client : JolokiaClient
  ): JolokiaAvailableChecker = new JolokiaAvailableChecker(client)
}

private[jolokia] class JolokiaAvailableChecker(
  client: JolokiaClient
) extends JolokiaChecker(client) {

  private val log : Logger = Logger[JolokiaAvailableChecker]

  override def toString: String = s"JolokiaAvailableCondition(${client.url}])"

  override def exec(client: JolokiaClient): Try[JolokiaObject] = client.version

  override def assertJolokia(obj: Try[JolokiaObject]): Boolean = obj match {
    case Success(v : JolokiaVersion) =>
      log.info(s"Jolokia [$v] discovered.")
      true
    case _ => false
  }
}
