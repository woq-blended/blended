package blended.itest.runner.internal

import akka.actor.Actor
import blended.itest.runner.TestTemplate
import blended.itest.runner.Protocol

class TestManager extends Actor {

  override def receive: Actor.Receive = running(List.empty)

  private def running(templates : List[TestTemplate]) : Actor.Receive = {
    
    case Protocol.AddTestTemplate(nf : TestTemplate) => 
      context.become(running(nf :: templates.filterNot(_.name == nf.name)))

    case Protocol.RemoveTestTemplate(n : String) => 
      context.become(running(templates.filterNot(_.name == n)))

    case Protocol.GetTestTemplates => 
      sender() ! Protocol.TestTemplates(templates)
  }
}
