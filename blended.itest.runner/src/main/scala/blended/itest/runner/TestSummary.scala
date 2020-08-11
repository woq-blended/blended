package blended.itest.runner

import java.text.SimpleDateFormat
import java.util.Date

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
  // How many failures have been encountered 
  failed : Int = 0,
  // How many successful runs have been executed 
  succeded : Int = 0,
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

object TestSummaryJMX {

  def create(sum : TestSummary) : TestSummaryJMX = {

    val sdf : SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS")

    TestSummaryJMX(
      aFactoryName = sum.factoryName,
      aTestName = sum.testName,
      pending = if (sum.maxExecutions == Int.MaxValue) {
        Int.MaxValue
      } else {
        sum.maxExecutions - sum.executions - sum.running
      },
      completed = sum.executions,
      completedFail = sum.failed,
      completedOk = sum.succeded,
      running = sum.running,
      lastStarted = sum.lastStarted.map(s => sdf.format(new Date(s))), 
      lastFailed = sum.lastFailed.map(f => sdf.format(new Date(f.timestamp))),
      lastFailedid = sum.lastFailed.map(_.id),
      lastErrorMsg = sum.lastFailed.flatMap(_.cause).map(_.getMessage()).getOrElse(""),
      lastSuccess = sum.lastSuccess.map(f => sdf.format(new Date(f.timestamp))),
      lastSuccessid = sum.lastSuccess.map(_.id),
      lastExecutions = sum.lastExecutions.map(_.id).toArray
    )
  }
}

case class TestSummaryJMX(
  aFactoryName : String,
  aTestName : String,
  pending : Int,
  completed : Int,
  completedFail : Int, 
  completedOk : Int,
  running : Int, 
  lastStarted : Option[String],
  lastFailed : Option[String],
  lastFailedid : Option[String],
  lastErrorMsg : String,
  lastSuccess : Option[String],
  lastSuccessid : Option[String],
  lastExecutions : Array[String]
)