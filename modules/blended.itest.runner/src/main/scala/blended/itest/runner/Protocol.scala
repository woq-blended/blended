package blended.itest.runner

object Protocol {
  
  // test execution related messages 
  case object StartTest
    
  case object GetTestTemplates
  case class TestTemplates(templates : List[TestTemplate])
  case class AddTestTemplateFactory(f : TestTemplateFactory)
  case class RemoveTestTemplateFactory(n : TestTemplateFactory)
  
}
