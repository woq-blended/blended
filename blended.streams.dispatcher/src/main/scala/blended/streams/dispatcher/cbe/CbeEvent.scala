package blended.streams.dispatcher.cbe

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

import blended.streams.transaction.EventSeverity.EventSeverity
import blended.streams.transaction.FlowTransactionState._
import blended.streams.transaction.{EventSeverity, FlowTransactionState}

import scala.concurrent.duration._

object CbeEvent {

  def apply(
    id           : String,
    component    : CbeComponent,
    state        : FlowTransactionState.FlowTransactionState,
    properties   : Map[String, Object],
    closeProcess : Boolean,
    timeout      : Long
  ) : CbeTransactionEvent = {

    val sev = state match {
      case Failed => EventSeverity.Critical
      case _ => EventSeverity.Information
    }

    new CbeTransactionEvent(
      id = id,
      severity = sev,
      component = component,
      state = Some(state),
      properties = properties,
      timestamp = new Date(),
      closeProcess = closeProcess,
      timeout = FiniteDuration(timeout, TimeUnit.MILLISECONDS)
    )
  }
}

case class CbeTransactionEvent(
  id           : String,
  severity     : EventSeverity,
  component    : CbeComponent,
  state        : Option[FlowTransactionState.FlowTransactionState],
  properties   : Map[String, Object],
  timestamp    : Date,
  closeProcess : Boolean,
  timeout      : FiniteDuration,
  msg          : Option[String] = None,
  situation    : String = "STATUS"
) {

  def asCBE() : String = {

    val ignoreHeader = List("Application", "Module", "ModuleLast")

    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
    val intSeverity : Int = severity

    val cbeMsg = msg match {
      case None => s"Transaction [$id] state [${state.getOrElse("n/a")}]"
      case Some(s) => s
    }

    val dataElements = ExtendedDataElements(
      properties.filterKeys(!ignoreHeader.contains(_)).map{ case (k,v) => (k, v.toString)}
    )

    val application = ExtendedDataElements(Map(
      "Module" -> component.subComponent,
      "Application" -> component.application
    ))

    val moduleLast = if (closeProcess)
      ExtendedDataElement("ModuleLast", component.subComponent).element
    else
      ""

    val transaction = state.map { s =>
      s"""
         |  <extendedDataElements name="TRANSACTION" type="noValue">
         |    <children name="TRANSACTION_ID" type="string">
         |      <values>$id</values>
         |    </children>
         |
         |    <children name="TRANSACTION_STATUS" type="string">
         |      <values>$s</values>
         |    </children>
         |""".stripMargin + (if (intSeverity >= EventSeverity.Critical)
        """
          |    <children name="TRANSACTION_FAILURE_REASON" type="string">
          |      <values>$event.transaction.reason</values>
          |    </children>#end
          |
          """.stripMargin
      else ""
        ) + "  </extendedDataElements>"
    }.getOrElse("")

    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<CommonBaseEvent
       |  msg="$cbeMsg"
       |  creationTime="${sdf.format(timestamp)}"
       |  globalInstanceId="${component.instanceId}"
       |  severity="$intSeverity"
       |  version="1.0.1"
       |  extensionName="noValue">
       |$transaction
       |$dataElements
       |$application
       |$moduleLast
       |${component.element}
       |
        |  <situation categoryName="ReportSituation">
       |    <situationType type="ReportSituation" reportCategory="$situation" reasoningScope="INTERNAL" />
       |  </situation>
       |</CommonBaseEvent>""".stripMargin
  }

}

