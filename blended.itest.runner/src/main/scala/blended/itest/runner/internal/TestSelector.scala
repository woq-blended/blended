package blended.itest.runner.internal

import blended.itest.runner.TestSummary
import blended.itest.runner.TestTemplate

trait TestSelector {
  def selectTest(f : List[TestTemplate], s : List[TestSummary]) : Option[TestTemplate]
}

object StandardTestSelector extends TestSelector {
  
  def selectTest(f : List[TestTemplate], s : List[TestSummary]) : Option[TestTemplate] = {
   
    val summary : TestTemplate => TestSummary = t => 
      s.find(sum => sum.factoryName == t.factory.name && sum.testName == t.name).getOrElse(TestSummary(t))
    
    val candidates : List[(TestTemplate, TestSummary)] = f.map(fact => (fact, summary(fact)))

    candidates.find { case (fact, sum) => sum.lastStarted.isEmpty } match {

      case Some(p) => Some(p._1)

      case _ => 
        candidates
          // An additional instance can be executed
          .filter{ case (fact, sum) => sum.running == 0 || fact.allowParallel }              
          // We have not yet executed / started all allowed executions
          .filter{ case (fact, sum) => sum.running + sum.executions < fact.maxExecutions }
          // We sort by the lastStarted element to find the factory that has the oldest start 
          .sortBy(_._2.lastStarted)
          // select the first
          .headOption
          .map(_._1)
    }
  }
}
