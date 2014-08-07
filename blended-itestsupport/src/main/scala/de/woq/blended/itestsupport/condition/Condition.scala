package de.woq.blended.itestsupport.condition

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

trait Condition {

  def satisfied : Boolean
  def timeout   : FiniteDuration = defaultTimeout
  def interval  : FiniteDuration = defaultInterval

  lazy val config = {
    val config = ConfigFactory.load()
    config.getConfig("de.woq.blended.itestsupport.condition")
  }

  private def defaultTimeout = config.getLong("defaultTimeout").millis

  private def defaultInterval = config.getLong("checkfrequency").millis
}
