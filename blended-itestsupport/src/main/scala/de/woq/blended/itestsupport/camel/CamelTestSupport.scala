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

import scala.Left
import scala.Right
import scala.collection.convert.Wrappers.JMapWrapper

import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.ExchangePattern
import org.apache.camel.Message
import org.apache.camel.impl.DefaultExchange

import akka.camel.CamelMessage
import de.woq.blended.itestsupport.BlendedIntegrationTestSupport
import de.woq.blended.testsupport.XMLMessageFactory
import de.woq.blended.util.FileReader

trait CamelTestSupport { this : BlendedIntegrationTestSupport =>

  private [CamelTestSupport] val log = system.log
  
  def sendTestMessage(message: String, uri: String, binary: Boolean): Either[Exception, CamelMessage] = {
    sendTestMessage(message, Map.empty, uri, binary)
  }

  def sendTestMessage(message: String, properties: Map[String, String], uri: String, binary: Boolean) : Either[Exception, CamelMessage] = {
    sendTestMessage(message, properties, uri, true, binary)
  }

  def sendTestMessage(message: String, properties: Map[String, String], uri: String, evaluateXML: Boolean, binary: Boolean) : Either[Exception, CamelMessage] = {

    val exchange = camelExchange(createMessage(message, properties, evaluateXML, binary))
    exchange.setPattern(ExchangePattern.InOnly)

    val producer = camel.template
    val response : Exchange = producer.send(uri, exchange)

    if (response.getException != null) {
      log.warning(s"Message not sent to [$uri]")
      Left(response.getException())
    }
    else {
      log.info(s"Sent test message to [$uri]")
      Right(camelMessage(exchange.getIn))
    }
  }

  def executeRequest(message: String, properties : Map[String, String], uri: String, binary: Boolean) : Either[Exception, CamelMessage] = {
    executeRequest(message, properties, uri, true, binary)
  }

  def executeRequest(message: String, properties : Map[String, String], uri: String, evaluateXML: Boolean, binary: Boolean) : Either[Exception, CamelMessage] = {
    val exchange = camelExchange(createMessage(message, properties, evaluateXML, binary))
    exchange.setPattern(ExchangePattern.InOut)

    val producer = camel.template
    val response = producer.send(uri, exchange)
    
    if (response.getException != null) {
      log.warning(s"Executing request on [$uri] failed")
      Left(response.getException)
    } else {
      Right(camelMessage(response.getOut))
    }
  }

  def headerExists(msg: CamelMessage, headerName: String) = msg.getHeaders.containsKey(headerName)

  def missingHeaderNames(exchange: CamelMessage, mandatoryHeaders: List[String]) =
    mandatoryHeaders.filter( headerName => !headerExists(exchange, headerName))

  def createMessage(message: String, properties: Map[String, String], evaluateXML: Boolean, binary: Boolean) : CamelMessage = 
    (evaluateXML match {
      case true => createMessageFromXML(message, binary)
      case false => createMessageFromFile(message, properties)
    }) match {
      case None =>
        log.info(s"Using text as msg body: [$message]")
        CamelMessage(message, properties)
      case Some(m) => m
    }

  private[this] def createMessageFromFile(message: String, props: Map[String, String]) : Option[CamelMessage] = {
    try {
      val content: Array[Byte] = FileReader.readFile(message)
      log.info("Body length is [" + content.length + "]")
      Some(CamelMessage(content, props.mapValues { _.asInstanceOf[Any] } ))
    } catch {
      case e: Exception => None
    }
  }

  private[this] def createMessageFromXML(message: String, binary: Boolean): Option[CamelMessage] = {
    try {
      binary match {
        case true  => Some(camelMessage(new XMLMessageFactory(message).createBinaryMessage()))
        case false => Some(camelMessage(new XMLMessageFactory(message).createTextMessage()))
      }
    } catch {
      case e: Exception => None
    }
  }

  private[this] def camelMessage(msg: Message) : CamelMessage = 
    CamelMessage(msg.getBody, JMapWrapper(msg.getHeaders).mapValues { _.asInstanceOf[Any] }.toMap)
    
  private[this] def camelExchange(msg: CamelMessage) : Exchange = {
    val exchange = new DefaultExchange(camel.context)
    
    exchange.getIn.setBody(msg.body)
    msg.headers.foreach { case (k, v) => exchange.getIn.setHeader(k, v) }
    
    exchange
  }
  
}
