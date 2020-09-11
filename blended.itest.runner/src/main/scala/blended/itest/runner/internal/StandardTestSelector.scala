package blended.itest.runner.internal

import blended.itest.runner.{TestSummary, TestTemplate, TestSelector}
import blended.util.logging.Logger

class StandardTestSelector extends TestSelector {

  private val log : Logger = Logger[StandardTestSelector]

  private def logCandidates(s : String, c : List[(TestTemplate, TestSummary)]) : Unit = {
    log.trace(s + " : " + c.map(_._1).map(t => s"${t.factory.name}::${t.name}").mkString(","))
  }

  def selectTest(f : List[TestTemplate], s : List[TestSummary]) : Option[TestTemplate] = {

    val summary : TestTemplate => TestSummary = t =>
      s.find(sum => sum.factoryName == t.factory.name && sum.testName == t.name).getOrElse(TestSummary(t))

    val candidates : List[(TestTemplate, TestSummary)] = f.map(fact => (fact, summary(fact)))

    candidates.find { case (fact, sum) => sum.lastStarted.isEmpty } match {

      case Some(p) =>
        log.debug(s"Selecting template that has not been used yet [${p._1.factory.name}::${p._1.name}]")
        Some(p._1)

      case _ =>
        val startable = candidates.filter{ case (t,s) =>
          t.minStartDelay match {
            case None => true
            case Some(d) => d.toMillis <= System.currentTimeMillis() - s.lastStarted.getOrElse(0L)
          }
        }

        // We have not yet executed / started all allowed executions
        val pendingExecutions = startable.filter{ case (t,s) =>
          s.maxExecutions == Int.MaxValue || s.running.size + s.executions < s.maxExecutions
        }
        logCandidates("pending", pendingExecutions)

        // principally we could start an instance
        val canRun = pendingExecutions.filter{ case(t,s) => s.running.isEmpty || t.allowParallel }
        logCandidates("canRun", canRun)

        canRun
          // We sort by the lastStarted element to find the factory that has the oldest start
          .sortBy(_._2.lastStarted)
          // select the first
          .headOption
          .map(_._1)
    }
  }
}
