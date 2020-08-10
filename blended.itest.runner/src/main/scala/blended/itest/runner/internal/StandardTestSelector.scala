package blended.itest.runner.internal

import blended.itest.runner.{TestSummary, TestTemplate, TestSelector}
import blended.util.logging.Logger

class StandardTestSelector extends TestSelector {

  private val log : Logger = Logger[StandardTestSelector]

  private def logCandidates(s : String, c : List[(TestTemplate, TestSummary)]) : Unit = {
    log.debug(s + " : " + c.map(_._1).map(t => s"${t.factory.name}::${t.name}").mkString(","))
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
        // We have not yet executed / started all allowed executions
        val pendingExecutions = candidates.filter{ case (t,s) => s.running + s.executions < s.maxExecutions }
        logCandidates("pending", pendingExecutions)

        // principally we could start an instance 
        val canRun = pendingExecutions.filter{ case(t,s) => s.running == 0 || t.allowParallel }
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
