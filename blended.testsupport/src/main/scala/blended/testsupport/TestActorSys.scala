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

package blended.testsupport

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}

object TestActorSys {
  val uniqueId = new AtomicInteger(0)

  def apply(f : TestKit => Unit) = new TestActorSys("TestActorSys%05d".format(uniqueId.incrementAndGet()), f)
}

class TestActorSys(name : String, f : TestKit => Unit)
  extends TestKit(ActorSystem(name)) {

  try {
    system.log.info("Start TestKit[{}]", system.name)
    f(this)
  }
  finally {
    system.log.info("Shutting down TestKit[{}]", system.name)
    system.shutdown()
  }
}
