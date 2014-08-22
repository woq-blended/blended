package de.woq.blended.itestsupport

object BlendedTestContext {

  private var context : Map[String, Any] = Map.empty

  def set(key: String, value: Any) = {
    context += (key -> value)
    value
  }

  def apply(key: String) = context(key)

  def apply() = context
}
