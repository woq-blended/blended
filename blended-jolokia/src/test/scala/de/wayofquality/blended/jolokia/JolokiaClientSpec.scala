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

package de.wayofquality.blended.jolokia

import akka.actor.Props
import akka.testkit.TestActorRef
import akka.util.Timeout
import de.wayofquality.blended.jolokia.model._
import de.wayofquality.blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.duration._

import de.wayofquality.blended.jolokia.protocol._

import scala.util.Failure

class JolokiaJVM extends JolokiaClient with JolokiaAddress {
  override val jolokiaUrl = "http://localhost:7777/jolokia"
}

class JolokiaFake extends JolokiaClient with JolokiaAddress {
  override val jolokiaUrl = "http://localhost:43888/jolokia"
}

class JolokiaClientSpec extends TestActorSys
  with WordSpecLike
  with Matchers {

  implicit val timeout = new Timeout(3.seconds)

  "The Jolokia client" should {

    "Connect to Jolokia" in {
      val jolokia = TestActorRef(Props[JolokiaJVM])
      jolokia ! GetJolokiaVersion
      expectMsgAnyClassOf(classOf[JolokiaVersion])
    }

    "Allow to search for MBeans by domain only" in {
      val jolokia = TestActorRef(Props[JolokiaJVM])
      jolokia ! SearchJolokia(new MBeanSearchDef {
        override def jmxDomain = "java.lang"
      })
      fishForMessage() {
        case JolokiaSearchResult(mbeanNames) => mbeanNames.size > 0
        case _ => false
      }
    }

    "Allow to search for MBeans by domain and properties" in {
      val jolokia = TestActorRef(Props[JolokiaJVM])
      jolokia ! SearchJolokia(new MBeanSearchDef {
        override def jmxDomain = "java.lang"
        override def searchProperties = Map( "type" -> "Memory" )
      })
      fishForMessage() {
        case JolokiaSearchResult(mbeanNames) => mbeanNames.size > 0
        case _ => false
      }
    }

    "Allow to read a specific MBean" in {
      val jolokia = TestActorRef(Props[JolokiaJVM])
      jolokia ! ReadJolokiaMBean("java.lang:type=Memory")
      fishForMessage() {
        case JolokiaReadResult(objName, _) => objName == "java.lang:type=Memory"
        case _ => false
      }
    }

    "Allow to execute a given operation on a MBean" in {
      val jolokia = TestActorRef(Props[JolokiaJVM])
      jolokia ! ExecJolokiaOperation(new OperationExecDef {
        override def objectName = "java.lang:type=Threading"
        override def operationName = "dumpAllThreads"
        override def parameters = List("true", "true")
      })
      fishForMessage() {
        case JolokiaExecResult(objName, operation, _) => objName == "java.lang:type=Threading" && operation == "dumpAllThreads"
        case _ => false
      }
    }
    
    "Respond with a failure if the rest call fails" in {
      val jolokia = TestActorRef(Props[JolokiaFake])
      jolokia ! GetJolokiaVersion
      fishForMessage() {
        case Failure(error) => true
      }
    }
  }

}
