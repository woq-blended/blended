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

package blended.akka

import akka.actor.Props
import akka.pattern.ask
import akka.testkit.TestActorRef
import akka.util.Timeout
import blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

object OSGIActorDummy {
  def apply(actorConfig: OSGIActorConfig)= new OSGIActorDummy(actorConfig)
}

class OSGIActorDummy(actorConfig: OSGIActorConfig) extends OSGIActor(actorConfig) {
  
  def receive = {
    case "invoke" =>
      sender ! withService[TestInterface1, String] {
        case Some(svc) => svc.name
        case _ => ""
      }
  }
}

class OSGIActorSpec extends TestActorSys 
  with WordSpecLike
  with Matchers 
  with TestSetup 
  with MockitoSugar {


  "An OSGIActor" should {

    implicit val timeout = Timeout(1.second)

    "allow to invoke a service" in {

      val probe = TestActorRef(Props(OSGIActorDummy(testActorConfig("foo", system))), "testActor")

      Await.result(probe ?  "invoke", 3.seconds) should be ("Andreas")
    }
  }

}
