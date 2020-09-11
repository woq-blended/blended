package blended.itest.runner.internal

import org.scalatest.matchers.should.Matchers
import blended.itest.runner._
import scala.util.Try
import akka.actor.ActorSystem
import blended.testsupport.scalatest.LoggingFreeSpecLike
import akka.testkit.TestKit
import akka.testkit.TestProbe
import java.util.concurrent.atomic.AtomicInteger

class TestManagerStateSpec extends TestKit(ActorSystem("StateSpec"))
  with LoggingFreeSpecLike
  with Matchers {

  private def templateFactory(n : String, cnt : Int) : TestTemplateFactory = new TestTemplateFactory() { f =>

    override def name : String = n

    override val templates: List[TestTemplate] = (1.to(cnt)).map { n =>
      new TestTemplate() {
        override def factory: TestTemplateFactory = f
        override val name : String = s"myTest-$n"
        override def test() : Try[Unit] = Try{}
        override def maxExecutions: Long = 5
        override def allowParallel: Boolean = false
      }
    }.toList
  }

  "The Test Manager State should" - {

    "start empty" in {

      val s : TestManagerState = TestManagerState()

      s.summaries should be (empty)
      s.templates should be (empty)
      s.executing should be (empty)
    }

    "allow to add and remove  a template factory" in {

      val f1 : TestTemplateFactory = templateFactory("fact1", 5)
      val f2 : TestTemplateFactory = templateFactory("fact2", 10)

      val s : TestManagerState =
        TestManagerState()
          .addTemplates(f1)
          .addTemplates(f2)

      s.templates.size should be (f1.templates.size + f2.templates.size)
      assert(s.templates.forall(t => List(f1.name, f2.name).contains(t.factory.name)))
    }

    "start with an empty summary for each registered template" in {
      val f1 : TestTemplateFactory = templateFactory("fact1", 5)
      val f2 : TestTemplateFactory = templateFactory("fact2", 10)

      val s : TestManagerState =
        TestManagerState()
          .addTemplates(f1)
          .addTemplates(f2)

      (f1.templates ::: f2.templates).foreach{ t =>
        val sum : TestSummary = s.summary(t)
        sum.lastStarted should be (None)
        sum.lastFailed should be (None)
        sum.executions should be (0)
      }
    }

    "should reflect a test start correctly" in {
      val f1 : TestTemplateFactory = templateFactory("fact1", 5)
      val t : TestTemplate = f1.templates.head

      val p : TestProbe = TestProbe()
      val id : String = "1"
      val s : TestManagerState = TestManagerState().addTemplates(f1).testStarted(id, t, p.ref)

      s.summary(t).lastStarted should be (defined)
      s.summary(t).running.get(id) should be (defined)

      s.testStarted("2", t, p.ref).summary(t).running should have size (2)
    }

    "should reflect a test completion correctly" in {

      val cnt : AtomicInteger = new AtomicInteger(0)

      val p : TestProbe = TestProbe()

      val f1 : TestTemplateFactory = templateFactory("fact1", 5)
      val t : TestTemplate = f1.templates.head

      val initialState : TestManagerState = TestManagerState().addTemplates(f1)

      def startTest(t : TestTemplate, state : TestManagerState) : TestManagerState = {
        val id : String = s"${cnt.incrementAndGet()}"
        state.testStarted(id, t, p.ref)
      }

      val event : String => TestEvent.State.State => TestEvent = id => s => TestEvent(
        factoryName = t.factory.name,
        testName = t.name,
        id = id,
        state = s,
        timestamp = System.currentTimeMillis() - 100
      )

      val succeeded : TestManagerState = startTest(t, initialState).testFinished(event("1")(TestEvent.State.Success))

      val sum1 : TestSummary = succeeded.summary(t)

      sum1.running should be (empty)
      sum1.lastSuccess should be (defined)
      sum1.lastFailed should be (None)
      sum1.executions should be (1)

      val failed : TestManagerState = startTest(t, succeeded).testFinished(event("2")(TestEvent.State.Failed))
      val sum2 : TestSummary = failed.summary(t)

      failed.summaries should have size(1)

      sum2.running should be (empty)
      sum2.lastSuccess should be (defined)
      sum2.lastFailed should be (defined)
      sum2.executions should be (2)
    }
  }
}
