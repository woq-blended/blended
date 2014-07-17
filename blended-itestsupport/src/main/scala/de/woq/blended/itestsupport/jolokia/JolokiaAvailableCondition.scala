package de.woq.blended.itestsupport.jolokia

import de.woq.blended.itestsupport.condition.Condition

class JolokiaAvailableCondition(
  jolokiaUrl: String, user: Option[String] = None, password: Option[String] = None
) extends Condition {
  override def satisfied = false
}
