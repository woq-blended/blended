package blended.streams.dispatcher.cbe

import java.text.{DateFormat, SimpleDateFormat}
import java.util.Date
import java.util.concurrent.TimeUnit

import blended.streams.transaction.EventSeverity.EventSeverity
import blended.streams.transaction.{EventSeverity, FlowTransactionState, FlowTransactionStateFailed}

import scala.concurrent.duration._

object CbeEvent {

  def apply(
    id           : String,
    component    : CbeComponent,
    state        : FlowTransactionState,
    properties   : Map[String, Object],
    closeProcess : Boolean,
    timeout      : Long
  ) : CbeTransactionEvent = {

    val sev = state match {
      case FlowTransactionStateFailed => EventSeverity.Critical
      case _ => EventSeverity.Information
    }

    CbeTransactionEvent(
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
  state        : Option[FlowTransactionState],
  properties   : Map[String, Object],
  timestamp    : Date,
  closeProcess : Boolean,
  timeout      : FiniteDuration,
  msg          : Option[String] = None,
  situation    : String = "STATUS"
) {

  private val ignoreHeader : List[String] = List("Application", "Module", "ModuleLast")

  private val sdf : DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
  private val intSeverity : Int = severity

  private val cbeMsg : String = msg match {
    case None    => s"Transaction [$id] state [${state.getOrElse("n/a")}]"
    case Some(s) => s
  }

  private val dataElements : String = ExtendedDataElements(
    properties.filterKeys(!ignoreHeader.contains(_)).map { case (k, v) => (k, v.toString) }.toMap
  )

  private val application : String = ExtendedDataElements(Map(
    "Module" -> component.subComponent,
    "Application" -> component.application
  ))

  private val moduleLast : String = if (closeProcess) {
    ExtendedDataElement("ModuleLast", component.subComponent).element
  } else {
    ""
  }

  private val severityElement : String = if (intSeverity >= EventSeverity.Critical) {
    """
      |    <children name="TRANSACTION_FAILURE_REASON" type="string">
      |      <values>$event.transaction.reason</values>
      |    </children>#end
      |
          """.stripMargin
  } else {
    ""
  }

  private val transaction : String = state.map { s =>
    s"""
       |  <extendedDataElements name="TRANSACTION" type="noValue">
       |    <children name="TRANSACTION_ID" type="string">
       |      <values>$id</values>
       |    </children>
       |
         |    <children name="TRANSACTION_STATUS" type="string">
       |      <values>$s</values>
       |    </children>
       |""".stripMargin +
      severityElement +
      "  </extendedDataElements>"
  }.getOrElse("")

  def asCBE() : String =
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

