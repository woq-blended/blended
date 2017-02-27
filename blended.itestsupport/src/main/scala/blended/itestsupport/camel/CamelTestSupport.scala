package blended.itestsupport.camel

import akka.camel.CamelMessage
import blended.testsupport.XMLMessageFactory
import blended.util.FileReader
import org.apache.camel.impl.DefaultExchange
import org.apache.camel.{CamelContext, Exchange, ExchangePattern, Message}
import org.slf4j.LoggerFactory

import scala.collection.convert.Wrappers.JMapWrapper

trait CamelTestSupport {

  val camelContext : CamelContext
  
  def sendTestMessage(message: String, uri: String, binary: Boolean)(implicit context: CamelContext): Either[Exception, CamelMessage] = {
    sendTestMessage(message, Map.empty, uri, binary)
  }

  def sendTestMessage(message: String, properties: Map[String, String], uri: String, binary: Boolean)(implicit context: CamelContext) : Either[Exception, CamelMessage] = {
    sendTestMessage(message, properties, uri, true, binary)
  }

  def sendTestMessage(message: String, properties: Map[String, String], uri: String, evaluateXML: Boolean, binary: Boolean)(implicit context: CamelContext) : Either[Exception, CamelMessage] = {

    val log = LoggerFactory.getLogger(classOf[CamelTestSupport])
    
    val exchange = camelExchange(createMessage(message, properties, evaluateXML, binary))
    exchange.setPattern(ExchangePattern.InOnly)

    val producer = context.createProducerTemplate()
    val response : Exchange = producer.send(uri, exchange)

    if (response.getException != null) {
      log.warn(s"Message not sent to [$uri]")
      Left(response.getException())
    }
    else {
      log.info(s"Sent test message to [$uri]")
      Right(camelMessage(exchange.getIn))
    }
  }

  def executeRequest(message: String, properties : Map[String, String], uri: String, binary: Boolean)(implicit context: CamelContext) : Either[Exception, CamelMessage] = {
    executeRequest(message, properties, uri, true, binary)
  }

  def executeRequest(message: String, properties : Map[String, String], uri: String, evaluateXML: Boolean, binary: Boolean)(implicit context: CamelContext) : Either[Exception, CamelMessage] = {

    val log = LoggerFactory.getLogger(classOf[CamelTestSupport])

    val exchange = camelExchange(createMessage(message, properties, evaluateXML, binary))
    exchange.setPattern(ExchangePattern.InOut)

    val producer = context.createProducerTemplate()
    val response = producer.send(uri, exchange)

    Option(response.getException()) match {
      case Some(e) =>
        log.warn(s"Executing request on [$uri] failed")
        Left(e)
      case None =>
        Right(camelMessage(response.getOut()))
    }
  }

  def headerExists(msg: CamelMessage, headerName: String) = msg.getHeaders.containsKey(headerName)

  def missingHeaderNames(exchange: CamelMessage, mandatoryHeaders: List[String]) =
    mandatoryHeaders.filter( headerName => !headerExists(exchange, headerName))

  def camelMessage(msg: Message)(implicit context: CamelContext) : CamelMessage =
    CamelMessage(msg.getBody, JMapWrapper(msg.getHeaders).mapValues { _.asInstanceOf[Any] }.toMap)

  private [CamelTestSupport] def createMessage(message: String, properties: Map[String, String], evaluateXML: Boolean, binary: Boolean)(implicit context: CamelContext) : CamelMessage =
    (evaluateXML match {
      case true => createMessageFromXML(message, binary)
      case false => createMessageFromFile(message, properties)
    }) match {
      case None =>
        CamelMessage(message, properties)
      case Some(m) => m
    }

  private[CamelTestSupport] def createMessageFromFile(message: String, props: Map[String, String])(implicit context: CamelContext) : Option[CamelMessage] = {
    try {
      val content: Array[Byte] = FileReader.readFile(message)
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
