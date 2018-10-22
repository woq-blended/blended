package blended.streams.dispatcher.internal.cbe

import java.util.Date

import blended.streams.transaction.{EventSeverity, FlowTransactionState}
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.XMLSupport
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.duration._

class TransactionEventSpec extends LoggingFreeSpec
  with Matchers {

  private val log = Logger[TransactionEventSpec]

  val headers = Map(
    "foo" -> "bar",
    "Application" -> "XX",
    "Module" -> "YY"
  )

  val comp = CbeComponent(
    "SIB-2.0",
    "TestComponent",
    "cc-9999",
    "Shop",
    "TestRoute",
    "ResourceType",
    "Route",
    9999
  )

  private[this] def validateCBE(event : TransactionEvent, xml : String) : XMLSupport = {

    val component = event.component

    val xmlSupport = new XMLSupport(xml)
    xmlSupport.validate("cbe_1.0.1_kl.xsd")
    xmlSupport.applyXPath("//*[local-name() = 'sourceComponentId']/@component") should be (component.component)
    xmlSupport.applyXPath("//*[local-name() = 'sourceComponentId']/@subComponent") should be (component.subComponent)
    xmlSupport.applyXPath("//*[local-name() = 'sourceComponentId']/@instanceId") should be (component.instanceId.toString)
    xmlSupport.applyXPath("//*[local-name() = 'children' and ./@name = 'TRANSACTION_STATUS']/*/text()") should be (event.state.get.toString)
    xmlSupport.applyXPath("//*[local-name() = 'children' and ./@name = 'TRANSACTION_ID']/*/text()") should be (event.id)
    xmlSupport.applyXPath("//*[local-name() = 'extendedDataElements' and ./@name = 'Application']/*/text()") should be ("SIB-2.0")
    xmlSupport.applyXPath("//*[local-name() = 'extendedDataElements' and ./@name = 'Module']/*/text()") should be (component.subComponent)

    xmlSupport
  }

  "A Transaction Event event" - {

    "be representable as a CBE XML" in {

      val event = TransactionEvent(
        id           = "myId",
        severity     = EventSeverity.Information,
        component    = comp,
        state        = Some(FlowTransactionState.Started),
        properties   = headers,
        closeProcess = false,
        timeout      = 1.second,
        timestamp    = new Date()
      )

      val xml = event.asCBE()
      validateCBE(event, xml)
    }

    "populate ModuleLast if the process is to be closed" in {
      val event = TransactionEvent(
        id           = "myId",
        severity     = EventSeverity.Information,
        component    = comp,
        state        = Some(FlowTransactionState.Started),
        properties   = headers,
        closeProcess = true,
        timeout      = 1.second,
        timestamp    = new Date()
      )

      val xml = event.asCBE()

      val xmlSupport = validateCBE(event, xml)
      xmlSupport.applyXPath("//*[local-name() = 'extendedDataElements' and ./@name = 'ModuleLast']/*/text()") should be (comp.subComponent)
    }
  }
}
