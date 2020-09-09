package blended.itestsupport.jolokia

import akka.actor.{ActorSystem, Props}
import blended.itestsupport.condition.AsyncCondition
import blended.jolokia.{JolokiaClient, JolokiaObject, JolokiaSearchResult, MBeanSearchDef}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}

object MBeanExistsCondition {

  def apply(
    client: JolokiaClient,
    searchDef: MBeanSearchDef,
    t : Option[FiniteDuration] = None
  )(implicit system: ActorSystem): AsyncCondition =
    AsyncCondition(
      Props(MBeanExistsChecker(client, searchDef)),
      s"MBeanExistsCondition(${client.url}, ${searchDef.pattern}})",
      t
    )
}

object CamelContextExistsCondition {
  def apply(
    client : JolokiaClient,
    contextName : String,
    t : Option[FiniteDuration] = None
  )(implicit system: ActorSystem): AsyncCondition = MBeanExistsCondition(
    client,
    MBeanSearchDef (
      jmxDomain = "org.apache.camel",
      searchProperties = Map(
        "type" -> "context",
        "name" -> s""""$contextName""""
      )
    )
  )
}

object JmsBrokerExistsCondition {
  def apply(
    client : JolokiaClient,
    brokerName : String,
    t : Option[FiniteDuration] = None
  )(implicit system: ActorSystem): AsyncCondition = MBeanExistsCondition(
    client,
    MBeanSearchDef (
      jmxDomain = "org.apache.activemq",
      searchProperties = Map(
        "type" -> "Broker",
        "brokerName" -> s""""$brokerName""""
      )
    )
  )
}

private[jolokia] object MBeanExistsChecker {
  def apply(
    client : JolokiaClient,
    searchDef: MBeanSearchDef
  ): MBeanExistsChecker = new MBeanExistsChecker(client, searchDef)
}

private[jolokia] class MBeanExistsChecker(
  client : JolokiaClient,
  searchDef : MBeanSearchDef
) extends JolokiaChecker(client) {

  override def toString: String = s"MbeanExistsCondition(${client.url}, ${searchDef.pattern}})"

  override def exec(client : JolokiaClient) : Try[JolokiaObject] = client.search(searchDef)

  override def assertJolokia (obj : Try[JolokiaObject]) : Boolean = obj match {
    case Success(r : JolokiaSearchResult) =>
      r.mbeanNames.nonEmpty
    case _ => false
  }
}
