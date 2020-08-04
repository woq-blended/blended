package blended.itest.runner

object TestSummary {
  def apply(f : TestTemplate) : TestSummary = TestSummary(f.name)
}

final case class TestSummary(
  // The test factory to instantiate single tests
  factoryName : String,
  // How often has the test been executed 
  executions : Int = 0,
  // How many instances are currently running 
  running : Int = 0,
  // When was the last instance started 
  lastStarted : Option[Long] = None,
  // the last failure
  lastFailed : Option[TestStatus] = None,
  // the last success
  lastSuccess : Option[TestStatus] = None,
  // How many last executions do we want to keep
  maxLastExecutions : Int = 10,
  // the last executions of this particular tests 
  lastExecutions : List[TestStatus] = List.empty
)