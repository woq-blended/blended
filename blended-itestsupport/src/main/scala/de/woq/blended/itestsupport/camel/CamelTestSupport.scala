package de.woq.blended.itestsupport.camel

import de.woq.blended.testsupport.XMLMessageFactory
import de.woq.blended.util.FileReader
import org.apache.camel._
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.{DefaultExchange, DefaultMessage}
import org.slf4j.{Logger, LoggerFactory}

trait CamelTestSupport { this : CamelContextProvider =>

  private final val LOGGER: Logger = LoggerFactory.getLogger(classOf[CamelTestSupport])

  def testContext = camelContext

  def sendTestMessage(message: String, uri: String) {
    sendTestMessage(message, Map.empty, uri)
  }

  def sendTestMessage(message: String, properties: Map[String, String], uri: String) {
    sendTestMessage(message, properties, uri, true)
  }

  def sendTestMessage(message: String, properties: Map[String, String], uri: String, evaluteXML: Boolean) : Boolean = {

    var msg: Option[Message] =  evaluteXML match {
      case true => createMessageFromXML(message)
      case false => createMessageFromFile(message, properties)
    }

    if (msg == None) {
      LOGGER.info("Using text as msg body: [{}]", message)
      msg = Some(new DefaultMessage)
      msg.get.setBody(message)
      addMessageProperties(msg, properties)
    }

    val sent = new DefaultExchange(camelContext, ExchangePattern.InOnly)
    sent.setIn(msg.get)

    val producer = camelContext.createProducerTemplate
    val response = producer.send(uri, sent)

    if (response.getException != null) {
      LOGGER.warn("Message not sent to [{}]", uri)
      false
    }
    else {
      LOGGER.info("Sent test message to [{}]", uri)
      true
    }
  }

  def headerExists(exchange: Exchange, headerName: String) = exchange.getIn.getHeader(headerName) != null

  def missingHeaderNames(exchange: Exchange, mandatoryHeaders: List[String]) =
    mandatoryHeaders.filter( headerName => !headerExists(exchange, headerName))

  private def createMessageFromFile(message: String, props: Map[String, String]) : Option[Message] = {

    try {
      val content: Array[Byte] = FileReader.readFile(message)
      val result = Some(new DefaultMessage)
      result.get.setBody(content)
      LOGGER.info("Body length is [" + content.length + "]")
      addMessageProperties(result, props)
      result
    } catch {
      case e: Exception => None
    }
  }

  private def createMessageFromXML(message: String): Option[Message] = {

    try {
      Some(new XMLMessageFactory(message).createMessage)
    } catch {
      case e: Exception => None
    }
  }

  private def addMessageProperties(message: Option[Message], props: Map[String, String]) : Option[Message] = {

    message.foreach { msg =>
      props.keys.foreach { propName =>
        val propValue = props(propName)
        LOGGER.info(s"Setting property [${propName}] = [${propValue}]")
        msg.setHeader(propName, propValue)
      }
    }
    message
  }

  def wireMock(mockName: String, uri: String): MockEndpoint = {
    val result: MockEndpoint = camelContext.getEndpoint("mock:" + mockName).asInstanceOf[MockEndpoint]

    camelContext.addRoutes(new RouteBuilder {
      def configure {
        from(uri).id(mockName).to(result)
      }
    })

    result
  }
}
