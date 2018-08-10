package blended.itestsupport

import blended.itestsupport.condition.{ AsyncCondition, Condition }
import org.apache.camel.CamelContext

package protocol {

  // Use this object to query an actor that encapsulates a condition.
  case object CheckCondition

  // Use this object to kick off an Asynchronous checker
  case class CheckAsyncCondition(condition: AsyncCondition)

  // This message collects the results of nested Conditions
  object ConditionCheckResult {
    def apply(results: List[ConditionCheckResult]) = {
      new ConditionCheckResult(
        results.map { r => r.satisfied }.flatten,
        results.map { r => r.timedOut }.flatten
      )
    }
  }

  case class ConditionCheckResult(satisfied: List[Condition], timedOut: List[Condition]) {
    def allSatisfied = timedOut.isEmpty

    def reportTimeouts: String =
      timedOut.mkString(
        s"\nA total of [${timedOut.size}] conditions have timed out", "\n", ""
      )
  }

  // Use this to kick off the creation of a TestContext based on configured Containers under Test
  case class TestContextRequest(cuts: Map[String, ContainerUnderTest])

  // This class returns a TestCamelContext that can be used for the integration tests or an Exception if
  // the context cannot be created
  case class TestContextResponse(context: Either[Throwable, CamelContext])

}

