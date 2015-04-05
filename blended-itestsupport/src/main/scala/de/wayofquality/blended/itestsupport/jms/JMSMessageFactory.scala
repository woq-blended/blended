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

package de.wayofquality.blended.itestsupport.jms

import javax.jms.{Session, Message}

import scala.util.Random

trait JMSMessageFactory {

  def createMessage(session: Session, content: Option[Any] = None) : Message
}

class FixedSizeMessageFactory(size: Int) extends JMSMessageFactory {

  private val rnd = new Random()

  private lazy val body = {
    val result = new Array[Byte](size)
    rnd.nextBytes(result)
    result
  }


  override def createMessage(session: Session, content: Option[Any]) = {
    val m = session.createBytesMessage()
    m.writeBytes(body)
    m
  }
}

class TextMessageFactory extends JMSMessageFactory {
  override def createMessage(session: Session, content: Option[Any]) = {
    val body = content match {
      case None => "None"
      case Some(m) => m.toString
    }
    session.createTextMessage(body)
  }
}
