package blended.itest.runner.internal

import akka.actor.Actor
import blended.itest.runner.TestFactory
import blended.itest.runner.Protocol

class TestManager extends Actor {

  override def receive: Actor.Receive = running(List.empty)

  private def running(factories : List[TestFactory]) : Actor.Receive = {
    
    case Protocol.AddTestFactory(nf : TestFactory) => 
      context.become(running(nf :: factories.filterNot(_.name == nf.name)))

    case Protocol.RemoveTestFactory(n : String) => 
      context.become(running(factories.filterNot(_.name == n)))

    case Protocol.GetTestFactories => sender() ! Protocol.TestFactories(factories)
  }
}
