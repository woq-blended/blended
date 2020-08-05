package blended.itest.runner.internal

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.matchers.should.Matchers
import blended.itest.runner.TestTemplate
import scala.util.Try
import blended.itest.runner.TestSummary
import blended.itest.runner.TestTemplateFactory

class TestSelectorSpec extends LoggingFreeSpec 
  with Matchers {

  private def templateFactory(cnt : Int) : TestTemplateFactory = new TestTemplateFactory() {
    
    private val f : TestTemplateFactory = this
    override def name : String = "myFactory"

    override val templates: List[TestTemplate] = (1.to(cnt)).map { n =>
      new TestTemplate() {
        override def factory: TestTemplateFactory = f
        override val name : String = s"myTest-$n"
        override def test() : Try[Unit] = Try{}
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

    "return None if no test templates are registered" in logException { 
      StandardTestSelector.selectTest(Nil, Map.empty) should be (None)
    }

    "select a single registered template" in logException {
      StandardTestSelector.selectTest(templateFactory(1).templates, Map.empty) match {
        case None => fail("Expected a selected test")
        case Some(t) => t.name should be ("myTest-1")
      }
    }

    "select the template that is not running in favor of one that has already been used" in logException { 

      val fact : TestTemplateFactory = templateFactory(2)
      val t1 : TestTemplate = template("myTest-1", fact.templates)
      val t2 : TestTemplate = template("myTest-2", fact.templates)

      val m : Map[String, TestSummary] = Map(
        t1.name -> TestSummary(t1).copy(lastStarted = Some(System.currentTimeMillis())),
        t2.name -> TestSummary(t2)
      )

      StandardTestSelector.selectTest(fact.templates, m) match {
        case None => fail("Expected a selected test")
        case Some(t) => t.name should be ("myTest-2")
      }
    }

    "select the template that has the oldest lastStarted timestamp" in {

      val fact : TestTemplateFactory = templateFactory(2)
      val t1 : TestTemplate = template("myTest-1", fact.templates)
      val t2 : TestTemplate = template("myTest-2", fact.templates)

      val m : Map[String, TestSummary] = Map(
        t1.name -> TestSummary(t1).copy(lastStarted = Some(2000L)),
        t2.name -> TestSummary(t2).copy(lastStarted = Some(1000L))
      )

      StandardTestSelector.selectTest(fact.templates, m) match {
        case None => fail("Expected a selected test")
        case Some(t) => t.name should be ("myTest-2")
      }
    }
  }  
}
