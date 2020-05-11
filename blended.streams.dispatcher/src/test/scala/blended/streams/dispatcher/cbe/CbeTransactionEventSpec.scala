package blended.streams.dispatcher.cbe

import java.util.Date

import blended.streams.transaction.{EventSeverity, FlowTransactionStateStarted}
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.XMLSupport
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class CbeEventSpec extends LoggingFreeSpec
  with Matchers {

  val headers : Map[String, String] = Map(
    "foo" -> "bar",
    "Application" -> "XX",
    "Module" -> "YY"
  )

  //scalastyle:off magic.number
  val comp : CbeComponent = CbeComponent(
    "SIB-2.0",
    "TestComponent",
    "cc-9999",
    "Shop",
    "TestRoute",
    "ResourceType",
    "Route",
    9999
  )
  // scalastyle:on magic.number

  private[this] def validateCBE(event : CbeTransactionEvent, xml : String) : XMLSupport = {

    val component = event.component

    val xmlSupport = new XMLSupport(xml)
    xmlSupport.validate("cbe_1.0.1_kl.xsd")
    xmlSupport.applyXPath("//*[local-name() = 'sourceComponentId']/@component") should be(component.component)
    xmlSupport.applyXPath("//*[local-name() = 'sourceComponentId']/@subComponent") should be(component.subComponent)
    xmlSupport.applyXPath("//*[local-name() = 'sourceComponentId']/@instanceId") should be(component.instanceId.toString)
    xmlSupport.applyXPath("//*[local-name() = 'children' and ./@name = 'TRANSACTION_STATUS']/*/text()") should be(event.state.get.toString)
    xmlSupport.applyXPath("//*[local-name() = 'children' and ./@name = 'TRANSACTION_ID']/*/text()") should be(event.id)
    xmlSupport.applyXPath("//*[local-name() = 'extendedDataElements' and ./@name = 'Application']/*/text()") should be("SIB-2.0")
    xmlSupport.applyXPath("//*[local-name() = 'extendedDataElements' and ./@name = 'Module']/*/text()") should be(component.subComponent)

    xmlSupport
  }

  "A Transaction Event event" - {

    "be representable as a CBE XML" in {

      val event = CbeTransactionEvent(
        id           = "myId",
        severity     = EventSeverity.Information,
        component    = comp,
        state        = Some(FlowTransactionStateStarted),
        properties   = headers,
        closeProcess = false,
        timeout      = 1.second,
        timestamp    = new Date()
      )

      val xml = event.asCBE()
      validateCBE(event, xml)
    }

    "populate ModuleLast if the process is to be closed" in {
      val event = CbeTransactionEvent(
        id           = "myId",
        severity     = EventSeverity.Information,
        component    = comp,
        state        = Some(FlowTransactionStateStarted),
        properties   = headers,
        closeProcess = true,
        timeout      = 1.second,
        timestamp    = new Date()
      )

      val xml = event.asCBE()

      val xmlSupport = validateCBE(event, xml)
      xmlSupport.applyXPath("//*[local-name() = 'extendedDataElements' and ./@name = 'ModuleLast']/*/text()") should be(comp.subComponent)
    }
  }
}
