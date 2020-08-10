package blended.itest.runner

object TestSummary {
  def apply(f : TestTemplate) : TestSummary = TestSummary(
    factoryName = f.factory.name, 
    testName = f.name,
    maxExecutions = f.maxExecutions
  )
}

final case class TestSummary(
  // The test factory to instantiate single tests
  factoryName : String,
  // The name of the test within the factory
  testName : String,
  // How many executions do we want of this test
  maxExecutions : Int,
  // How often has the test been executed 
  executions : Int = 0,
  // How many instances are currently running 
  running : Int = 0,
  // When was the last instance started 
  lastStarted : Option[Long] = None,
  // the last failure
  lastFailed : Option[TestEvent] = None,
  // the last success
  lastSuccess : Option[TestEvent] = None,
  // How many last executions do we want to keep
  maxLastExecutions : Int = 10,
  // the last executions of this particular tests 
  lastExecutions : List[TestEvent] = List.empty
) {
  override def toString() : String = {

    val suc : String = lastSuccess.map(e => s", lastSuccess=${e.toString()}").getOrElse("")
    val fail : String = lastFailed.map(e => s", lastFailed=${e.toString()}").getOrElse("")

    s"TestSummary($factoryName::$testName, executions=$executions/$maxExecutions, running=$running$suc$fail)"
  }
}