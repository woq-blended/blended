package blended.itest.runner

/** A simple interface to instantiate a single test. */
trait TestFactory {
  // a unique name for the test factory 
  def name : String   
  //def createTest(id : String) : ActorRef
}
