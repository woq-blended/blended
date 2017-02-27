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
