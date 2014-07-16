package de.woq.blended.itestsupport

import de.woq.blended.itestsupport.condition.Condition
import scala.concurrent.duration._

package object protocol {

  case object ResetPortRange
  case object GetPort
  case class FreePort(p: Int)

  case class CheckCondition(timeOut: FiniteDuration = 30.seconds)
  case class ConditionTimeOut(conditions : List[Condition])
  case class ConditionSatisfied(conditions: List[Condition])
}
