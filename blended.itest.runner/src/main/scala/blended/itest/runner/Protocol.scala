package blended.itest.runner

object Protocol {
  
  case object StartTest
  case object TestStarted 
  

  case object GetTestFactories
  case class TestFactories(factories : List[TestFactory])
  case class AddTestFactory(f : TestFactory)
  case class RemoveTestFactory(n : String)
  
}
