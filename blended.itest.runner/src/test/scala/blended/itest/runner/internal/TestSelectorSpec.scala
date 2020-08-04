package blended.itest.runner.internal

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.matchers.should.Matchers
import blended.itest.runner.TestTemplate
import scala.util.Try
import blended.itest.runner.TestSummary

class TestSelectorSpec extends LoggingFreeSpec 
  with Matchers {

  private def template(i : Int) : TestTemplate = new TestTemplate() { 
    override val name : String = s"myFactory-$i"
    override def test() : Try[Unit] = Try{}
  } 
  
  "The test template selector should" - {

    "return None if no test templates are registered" in logException { 
      StandardTestSelector.selectTest(Nil, Map.empty) should be (None)
    }

    "select a single registered template" in logException {
      StandardTestSelector.selectTest(List(template(1)), Map.empty) match {
        case None => fail("Expected a selected test")
        case Some(t) => t.name should be ("myFactory-1")
      }
    }

    "select the template that is not running in favor of one that has already been used" in logException { 

      val t1 : TestTemplate = template(1)
      val t2 : TestTemplate = template(2)

      val m : Map[String, TestSummary] = Map(
        t1.name -> TestSummary(t1).copy(lastStarted = Some(System.currentTimeMillis())),
        t2.name -> TestSummary(t2)
      )

      StandardTestSelector.selectTest(List(t1, t2), m) match {
        case None => fail("Expected a selected test")
        case Some(t) => t.name should be ("myFactory-2")
      }
    }

    "select the template that has the oldest lastStarted timestamp" in {

      val t1 : TestTemplate = template(1)
      val t2 : TestTemplate = template(2)

      val m : Map[String, TestSummary] = Map(
        t1.name -> TestSummary(t1).copy(lastStarted = Some(2000L)),
        t2.name -> TestSummary(t2).copy(lastStarted = Some(1000L))
      )

      StandardTestSelector.selectTest(List(t1, t2), m) match {
        case None => fail("Expected a selected test")
        case Some(t) => t.name should be ("myFactory-2")
      }
    }
  }  
}
