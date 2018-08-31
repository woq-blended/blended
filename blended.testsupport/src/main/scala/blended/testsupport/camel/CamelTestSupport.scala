package blended.testsupport.camel

import scala.collection.convert.Wrappers.JMapWrapper
import scala.util.Try

import akka.camel.CamelMessage
import blended.testsupport.XMLMessageFactory
import blended.util.FileHelper
import blended.util.logging.Logger
import org.apache.camel.{ CamelContext, Exchange, ExchangePattern, Message }
import org.apache.camel.impl.DefaultExchange

trait CamelTestSupport {

  val camelContext : CamelContext
  
  def sendTestMessage(message: String, uri: String, binary: Boolean)(implicit context: CamelContext): Try[CamelMessage] = {
    sendTestMessage(message, Map.empty, uri, binary)
  }

  def sendTestMessage(message: String, properties: Map[String, String], uri: String, binary: Boolean)(implicit context: CamelContext) : Try[CamelMessage] = {
    sendTestMessage(message, properties, uri, true, binary)
  }

  def sendTestMessage(message: String, properties: Map[String, String], uri: String, evaluateXML: Boolean, binary: Boolean)(implicit context: CamelContext) : Try[CamelMessage] ={
    sendTestMessage(createMessage(message, properties, evaluateXML, binary), uri)
  }

  def sendTestMessage(message: CamelMessage, uri: String)(implicit context: CamelContext) : Try[CamelMessage] = {
    val log = Logger[CamelTestSupport]

    Try {
      val exchange = camelExchange(message)
      exchange.setPattern(ExchangePattern.InOnly)

      log.info(s"sending test message to [$uri]")
      val producer = context.createProducerTemplate()
      val response : Exchange = producer.send(uri, exchange)

      if (response.getException != null) {
        log.warn(s"Message not sent to [$uri]")
        throw response.getException
      }
      else {
        log.info(s"Sent test message to [$uri]")
        camelMessage(exchange.getIn)
      }
    }
  }

  def executeRequest(message: String, properties : Map[String, String], uri: String, binary: Boolean)(implicit context: CamelContext) : Try[CamelMessage] = {
    executeRequest(message, properties, uri, true, binary)
  }

  def executeRequest(message: String, properties : Map[String, String], uri: String, evaluateXML: Boolean, binary: Boolean)(implicit context: CamelContext) : Try[CamelMessage] = {

    val log = Logger[CamelTestSupport]

    val exchange = camelExchange(createMessage(message, properties, evaluateXML, binary))
    exchange.setPattern(ExchangePattern.InOut)

    Try {
      val producer = context.createProducerTemplate()
      val response = producer.send(uri, exchange)

      Option(response.getException()) match {
        case Some(e) =>
          log.warn(s"Executing request on [$uri] failed")
          throw e
        case None =>
          camelMessage(response.getOut())
      }
    }
  }

  def headerExists(msg: CamelMessage, headerName: String) = msg.getHeaders.containsKey(headerName)

  def missingHeaderNames(exchange: CamelMessage, mandatoryHeaders: List[String]) =
    mandatoryHeaders.filter( headerName => !headerExists(exchange, headerName))

  def camelMessage(msg: Message)(implicit context: CamelContext) : CamelMessage =
    CamelMessage(msg.getBody, JMapWrapper(msg.getHeaders).mapValues { _.asInstanceOf[Any] }.toMap)

  def createMessage(message: String, properties: Map[String, String], evaluateXML: Boolean, binary: Boolean)(implicit context: CamelContext) : CamelMessage =
    (evaluateXML match {
      case true => createMessageFromXML(message, binary)
      case false => createMessageFromFile(message, binary, properties)
    }) match {
      case None =>
        CamelMessage(message, properties)
      case Some(m) => m
    }

  private[CamelTestSupport] def createMessageFromFile(message: String, binary: Boolean, props: Map[String, String])(implicit context: CamelContext) : Option[CamelMessage] = {
    try {
      val bytes = FileHelper.readFile(message)
      val content: Any = if (binary) bytes else new String(bytes)
      Some(CamelMessage(content, props.mapValues { _.asInstanceOf[Any] } ))
    } catch {
      case e: Exception => None
    }
  }

  private[CamelTestSupport] def createMessageFromXML(message: String, binary: Boolean)(implicit context: CamelContext) : Option[CamelMessage] = {
    try {
      binary match {
        case true  => Some(camelMessage(new XMLMessageFactory(message).createBinaryMessage()))
        case false => Some(camelMessage(new XMLMessageFactory(message).createTextMessage()))
      }
    } catch {
      case e: Exception => None
    }
  }


  private[this] def camelExchange(msg: CamelMessage)(implicit context: CamelContext) : Exchange = {

    val exchange = new DefaultExchange(context)
    
    exchange.getIn.setBody(msg.body)
    msg.headers.foreach { case (k, v) => exchange.getIn.setHeader(k, v) }
    
    exchange
  }
  
}
