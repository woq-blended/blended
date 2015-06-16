/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.akka.itest

import akka.actor.Props
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import blended.itestsupport.BlendedIntegrationTestSupport
import blended.itestsupport.camel.MockAssertions._
import blended.itestsupport.camel.protocol._
import blended.itestsupport.camel.{CamelMockActor, CamelTestSupport}
import org.scalatest.{DoNotDiscover, Matchers, WordSpec}

import scala.concurrent.duration.DurationInt

@DoNotDiscover
class BlendedDemoSpec(implicit testKit : TestKit) extends WordSpec
  with Matchers
  with BlendedIntegrationTestSupport 
  with CamelTestSupport {

  implicit val system = testKit.system
  implicit val timeOut = new Timeout(3.seconds)
  implicit val eCtxt = testKit.system.dispatcher

  private[this] val log = testKit.system.log

  "The demo container" should {

    "Define the sample Camel Route from SampleIn to SampleOut" in {

      val mock = TestActorRef(Props(CamelMockActor("jms:queue:SampleOut")))
      val mockProbe = new TestProbe(system)
      testKit.system.eventStream.subscribe(mockProbe.ref, classOf[MockMessageReceived])
 
      sendTestMessage("Hello Blended!", Map("foo" -> "bar"), "jms:queue:SampleIn", binary = false) match {
        // We have successfully sent the message 
        case Right(msg) =>
          // make sure the message reaches the mock actors before we start assertions
          mockProbe.receiveN(1)
          
          checkAssertions(mock, 
            expectedMessageCount(1), 
            expectedBodies("Hello Blended!"),
            expectedHeaders(Map("foo" -> "bar"))
          ) should be(List.empty) 
        // The message has not been sent
        case Left(e) => 
          log.error(e.getMessage, e)
          fail(e.getMessage)
      }
    }
  }
}