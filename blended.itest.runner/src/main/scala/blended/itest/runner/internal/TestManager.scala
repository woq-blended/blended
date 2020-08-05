package blended.itest.runner.internal

import akka.actor.Actor
import blended.itest.runner.TestTemplate
import blended.itest.runner.Protocol
import blended.itest.runner.TestTemplateFactory

class TestManager extends Actor {

  override def receive: Actor.Receive = running(List.empty)

  private def running(templates : List[TestTemplate]) : Actor.Receive = {
    
    case Protocol.AddTestTemplateFactory(fact : TestTemplateFactory) => 
      context.become(running(fact.templates ::: templates.filterNot(_.factory.name == fact.name)))

    case Protocol.RemoveTestTemplateFactory(fact : TestTemplateFactory) => 
      context.become(running(templates.filterNot(_.factory.name == fact.name)))

    case Protocol.GetTestTemplates => 
      sender() ! Protocol.TestTemplates(templates)
  }
}
