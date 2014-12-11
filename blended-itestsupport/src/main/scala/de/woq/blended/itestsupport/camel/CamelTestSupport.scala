/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.woq.blended.itestsupport.camel

import de.woq.blended.testsupport.XMLMessageFactory
import de.woq.blended.util.FileReader
import org.apache.camel._
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.{DefaultExchange, DefaultMessage}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.convert.Wrappers.JCollectionWrapper

trait CamelTestSupport {

  private final val LOGGER: Logger = LoggerFactory.getLogger(classOf[CamelTestSupport])

  def sendTestMessage(message: String, uri: String)(implicit context: CamelContext) : Either[Exception, Exchange] = {
    sendTestMessage(message, Map.empty, uri)
  }

  def sendTestMessage(message: String, properties: Map[String, String], uri: String)(implicit context: CamelContext) : Either[Exception, Exchange] = {
    sendTestMessage(message, properties, uri, true)
  }

  def sendTestMessage(message: String, properties: Map[String, String], uri: String, evaluteXML: Boolean)(implicit context: CamelContext) : Either[Exception, Exchange] = {

    val exchange = createExchange(message, properties, evaluteXML)
    exchange.setPattern(ExchangePattern.InOnly)

    val producer : ProducerTemplate = context.createProducerTemplate
    val response : Exchange = producer.send(uri, exchange)

    if (response.getException != null) {
      LOGGER.warn(s"Message not sent to [$uri]")
      Left(response.getException())
    }
    else {
      LOGGER.info(s"Sent test message to [$uri]")
      Right(response)
    }
  }

  def executeRequest(message: String, properties : Map[String, String], uri: String)(implicit context: CamelContext) : Exchange = {
    executeRequest(message, properties, uri, true)
  }

  def executeRequest(message: String, properties : Map[String, String], uri: String, evaluateXML: Boolean)(implicit context: CamelContext) : Exchange = {
    val exchange = createExchange(message, properties, evaluateXML)
    exchange.setPattern(ExchangePattern.InOut)

    val producer = context.createProducerTemplate
    producer.send(uri, exchange)
  }

  def headerExists(exchange: Exchange, headerName: String) = exchange.getIn.getHeader(headerName) != null

  def missingHeaderNames(exchange: Exchange, mandatoryHeaders: List[String]) =
    mandatoryHeaders.filter( headerName => !headerExists(exchange, headerName))

  private def createExchange(message: String, properties: Map[String, String], evaluateXML: Boolean)(implicit context: CamelContext) = {
    var msg: Option[Message] =  evaluateXML match {
      case true => createMessageFromXML(message)
      case false => createMessageFromFile(message, properties)
    }

    if (msg == None) {
      LOGGER.info(s"Using text as msg body: [$message]")
      msg = Some(new DefaultMessage)
      msg.get.setBody(message)
      addMessageProperties(msg, properties)
    }

    val sent = new DefaultExchange(context)
    sent.setIn(msg.get)

    sent
  }

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
        LOGGER.info(s"Setting property [$propName] = [$propValue]")
        msg.setHeader(propName, propValue)
      }
    }
    message
  }

  def wireMock(mockName: String, uri: String)(implicit context: CamelContext) : MockEndpoint = {

    val mockUri = s"mock://$mockName"

    val result = context.getEndpoint(mockUri).asInstanceOf[MockEndpoint]

    LOGGER.debug(s"Creating Route from [$uri] to [$mockUri].")
    context.addRoutes(new RouteBuilder {
      def configure() : Unit = {
        from(uri).id(mockName).to(mockUri)
      }
    })

    result
  }

  final def resetMockEndpoints(implicit context: CamelContext) : Unit = {
    JCollectionWrapper(context.getEndpoints)
      .filter(_.isInstanceOf[MockEndpoint])
      .foreach( ep => ep.asInstanceOf[MockEndpoint].reset())
  }

}
