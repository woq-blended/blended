package blended.itest.runner.internal

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.matchers.should.Matchers
import blended.itest.runner._
import scala.util.Try
import scala.concurrent.duration._
import blended.jmx.statistics.Accumulator

class StandardTestSelectorSpec extends LoggingFreeSpec
  with Matchers {

  private def templateFactory(cnt : Int, minDelay : Option[FiniteDuration] = None) : TestTemplateFactory = new TestTemplateFactory() { f =>

    override def name : String = "myFactory"

    override val templates: List[TestTemplate] = (1.to(cnt)).map { n =>
      new TestTemplate() {
        override def factory: TestTemplateFactory = f
        override val name : String = s"myTest-$n"
        override def test() : Try[Unit] = Try{}
        override def maxExecutions: Long = 5
        override def allowParallel: Boolean = false
        override def minStartDelay: Option[FiniteDuration] = minDelay
      }
    }.toList
  }

  private def template(name : String, templates : List[TestTemplate]) : TestTemplate = {
    templates.find(_.name == name) match {
      case Some(t) => t
      case None => throw new Exception("No such test template")
    }
  }

  "The test template selector should" - {

    val selector : TestSelector = new StandardTestSelector()

    "return None if no test templates are registered" in logException {
      selector.selectTest(Nil, Nil) should be (None)
    }

    "select a single registered template" in logException {
      selector.selectTest(templateFactory(1).templates, Nil) match {
        case None => fail("Expected a selected test")
        case Some(t) => t.name should be ("myTest-1")
      }
    }

    "select the template that is not running in favor of one that has already been used" in logException {

      val fact : TestTemplateFactory = templateFactory(2)
      val t1 : TestTemplate = template("myTest-1", fact.templates)
      val t2 : TestTemplate = template("myTest-2", fact.templates)

      val m : List[TestSummary] = List(
        TestSummary(t1).copy(lastStarted = Some(System.currentTimeMillis())),
        TestSummary(t2)
      )

      selector.selectTest(fact.templates, m) match {
        case None => fail("Expected a selected test")
        case Some(t) => t.name should be ("myTest-2")
      }
    }

    "select the template that has the oldest lastStarted timestamp" in {

      val fact : TestTemplateFactory = templateFactory(2)
      val t1 : TestTemplate = template("myTest-1", fact.templates)
      val t2 : TestTemplate = template("myTest-2", fact.templates)

      val m : List[TestSummary] = List(
        TestSummary(t1).copy(lastStarted = Some(2000L)),
        TestSummary(t2).copy(lastStarted = Some(1000L))
      )

      selector.selectTest(fact.templates, m) match {
        case None => fail("Expected a selected test")
        case Some(t) => t.name should be ("myTest-2")
      }
    }

    "not select a template that is already running and disallows parallel tests" in {

      val fact : TestTemplateFactory = templateFactory(1)
      val t1 : TestTemplate = template("myTest-1", fact.templates)

      val m : List[TestSummary] = List(
        TestSummary(t1).copy(lastStarted = Some(System.currentTimeMillis()), running = 1)
      )

      selector.selectTest(fact.templates, m) should be (None)
    }

    "not select a template that has reached its maximal executions" in {
      val fact : TestTemplateFactory = templateFactory(1)
      val t1 : TestTemplate = template("myTest-1", fact.templates)

      val m : List[TestSummary] = List(
        TestSummary(t1).copy(lastStarted = Some(System.currentTimeMillis()), succeded = Accumulator().copy(t1.maxExecutions))
      )

      selector.selectTest(fact.templates, m) should be (None)
    }

    "not select a template where the minimum start delay has not been passed" in {
      val fact : TestTemplateFactory = templateFactory(1, Some(60.seconds))
      val t1 : TestTemplate = template("myTest-1", fact.templates)

      val m : List[TestSummary] = List(
        TestSummary(t1).copy(lastStarted = Some(System.currentTimeMillis()))
      )

      selector.selectTest(fact.templates, m) should be (None)

      val m2 : List[TestSummary] = List(
        TestSummary(t1).copy(lastStarted = Some(System.currentTimeMillis() - 90.seconds.toMillis))
      )

      selector.selectTest(fact.templates, m2) match {
        case None => fail("Expected a selected test")
        case Some(t) => t.name should be ("myTest-1")
      }
    }
  }
}
