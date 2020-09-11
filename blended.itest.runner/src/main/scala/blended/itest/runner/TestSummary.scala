package blended.itest.runner

import java.text.SimpleDateFormat
import java.util.Date
import blended.jmx.statistics.Accumulator
import blended.util.logging.Logger

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
  maxExecutions : Long,
  // How many failures have been encountered
  failed : Accumulator = Accumulator(),
  // How many successful runs have been executed
  succeded : Accumulator = Accumulator(),
  // The currently running tests and the events that have started them
  running : Map[String, TestEvent] = Map.empty,
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

  private val log : Logger = Logger[TestSummary]

  private val isSuccess : TestEvent => Boolean = _.state == TestEvent.State.Success
  private val isFailed : TestEvent => Boolean = _.state == TestEvent.State.Failed

  def update(event : TestEvent) : TestSummary = {
    val updated : TestSummary = event match {
      case s if s.state == TestEvent.State.Started =>
        copy(
          running = running ++ Map(s.id -> s),
          lastStarted = lastStarted match {
            case None => Some(s.timestamp)
            case Some(t) => Some(Math.max(t, s.timestamp))
          }
        )

      case f =>
        val started : Long = running.get(f.id).map(_.timestamp).getOrElse(System.currentTimeMillis())

        copy(
          running = running.filter(_._1 != f.id),
          lastFailed = if (isFailed(f)) Some(f) else lastFailed,
          lastSuccess = if (isSuccess(f)) Some(f) else lastSuccess,
          failed = if (isFailed(f)) failed.record(f.timestamp - started) else failed,
          succeded = if (isSuccess(f)) succeded.record(f.timestamp - started) else succeded,
          lastExecutions = (f :: lastExecutions).take(maxLastExecutions)
        )
    }

    log.debug(s"Updated Test summary to [$updated]")
    updated
  }

  val executions : Long = failed.count + succeded.count

  override def toString() : String = {

    val suc : String = lastSuccess.map(e => s", lastSuccess=${e.toString()}").getOrElse("")
    val fail : String = lastFailed.map(e => s", lastFailed=${e.toString()}").getOrElse("")

    s"TestSummary($factoryName::$testName, executions=$executions/$maxExecutions, running=${running.size}$suc$fail)"
  }
}

object TestSummaryJMX {

  def create(sum : TestSummary) : TestSummaryJMX = {

    val sdf : SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS")

    TestSummaryJMX(
      aFactoryName = sum.factoryName,
      aTestName = sum.testName,
      pending = if (sum.maxExecutions == Long.MaxValue) {
        Long.MaxValue
      } else {
        sum.maxExecutions - sum.executions - sum.running.size
      },

      completedCnt = sum.succeded.count,
      completedMin = if (sum.succeded.count == 0) "" else sum.succeded.minMsec.toString(),
      completedMax = if (sum.succeded.count == 0) "" else sum.succeded.maxMsec.toString(),
      completedAvg = sum.succeded.avg,

      failedCnt = sum.failed.count,
      failedMin = if (sum.failed.count == 0) "" else sum.failed.minMsec.toString(),
      failedMax = if (sum.failed.count == 0) "" else sum.failed.maxMsec.toString(),
      failedAvg = sum.failed.avg,

      running = sum.running.view.keys.toList,

      lastStarted = sum.lastStarted.map(s => sdf.format(new Date(s))).getOrElse(""),
      lastFailed = sum.lastFailed.map(f => sdf.format(new Date(f.timestamp))).getOrElse(""),
      lastFailedid = sum.lastFailed.map(_.id).getOrElse(""),

      lastErrorMsg = sum.lastFailed.flatMap(_.cause).map(_.getMessage()).getOrElse(""),

      lastSuccess = sum.lastSuccess.map(f => sdf.format(new Date(f.timestamp))).getOrElse(""),
      lastSuccessid = sum.lastSuccess.map(_.id).getOrElse(""),
      lastExecutions = sum.lastExecutions.map(_.id).toList
    )
  }
}

case class TestSummaryJMX(
  aFactoryName : String,
  aTestName : String,
  pending : Long,

  completedCnt : Long,
  completedMin: String,
  completedMax: String,
  completedAvg: Double,

  failedCnt: Long,
  failedMin: String,
  failedMax: String,
  failedAvg: Double,

  running : List[String],

  lastStarted : String,
  lastFailed : String,
  lastFailedid : String,
  lastErrorMsg : String,
  lastSuccess : String,
  lastSuccessid : String,
  lastExecutions : List[String]
)