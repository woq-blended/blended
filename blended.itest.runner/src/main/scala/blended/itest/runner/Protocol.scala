package blended.itest.runner

object Protocol {
  
  // test execution related messages 
  case object StartTest
    
  case class TestStatusSchanged(s : TestStatus)

  case object GetTestTemplates
  case class TestTemplates(templates : List[TestTemplate])
  case class AddTestTemplate(f : TestTemplate)
  case class RemoveTestTemplate(n : String)
  
}
