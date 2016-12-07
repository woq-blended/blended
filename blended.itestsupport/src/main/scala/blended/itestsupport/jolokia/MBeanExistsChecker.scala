package blended.itestsupport.jolokia

import akka.actor.{ActorSystem, Props}
import blended.itestsupport.condition.AsyncCondition
import blended.jolokia.model.JolokiaSearchResult
import blended.jolokia.protocol._

import scala.concurrent.duration.FiniteDuration

object MBeanExistsCondition {

  def apply(
    url: String,
    user: Option[String] = None,
    pwd: Option[String] = None,
    searchDef: MBeanSearchDef,
    t : Option[FiniteDuration] = None
  )(implicit system: ActorSystem) =
    AsyncCondition(
      Props(MBeanExistsChecker(url, user, pwd, searchDef)),
      s"MBeanExistsCondition(${url}, ${searchDef.pattern}})",
      t
    )
}

object CamelContextExistsCondition {
  def apply(
    url: String,
    user: Option[String] = None,
    pwd: Option[String] = None,
    contextName : String,
    t : Option[FiniteDuration] = None
  )(implicit system: ActorSystem) = MBeanExistsCondition(
    url,
    user,
    pwd, new MBeanSearchDef {
      override def jmxDomain = "org.apache.camel"
      override def searchProperties = Map(
        "type" -> "context",
        "name" -> s""""${contextName}""""
      )
    },
    t
  )
}

object JmsBrokerExistsCondition {
  def apply(
    url: String,
    user: Option[String] = None,
    pwd: Option[String] = None,
    brokerName : String,
    t : Option[FiniteDuration] = None
  )(implicit system: ActorSystem) = MBeanExistsCondition(
    url,
    user,
    pwd, new MBeanSearchDef {
      override def jmxDomain = "org.apache.activemq"
      override def searchProperties = Map(
        "type" -> "Broker",
        "brokerName" -> s"${brokerName}"
      )
    },
    t
  )
}

private[jolokia] object MBeanExistsChecker {
  def apply(
    url: String,
    user: Option[String] = None,
    pwd: Option[String] = None,
    searchDef: MBeanSearchDef
  )(implicit system: ActorSystem) = new MBeanExistsChecker(url, user, pwd, searchDef)
}

private[jolokia] class MBeanExistsChecker(
  url: String,
  userName: Option[String] = None,
  userPwd: Option[String] = None,
  searchDef : MBeanSearchDef
)(implicit system:ActorSystem) extends JolokiaChecker(url, userName, userPwd) with JolokiaAssertion {

  override def toString = s"MbeanExistsCondition(${url}, ${searchDef.pattern}})"

  override def jolokiaRequest = SearchJolokia(searchDef)

  override def assertJolokia = { msg =>
    msg match {
      case v : JolokiaSearchResult => !v.mbeanNames.isEmpty
      case _ => false
    }
  }
}
