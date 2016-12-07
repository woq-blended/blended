package blended.itestsupport.condition

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

/**
 * A Condition encapsulates an assertion that may change over time. The use case is to
 * wait for a Condition to be satisfied at some - normally that is a pre condition that
 * must be fulfilled before the real tests are executed.
 */
trait Condition {

  /** Is the condition satisfied ? */
  def satisfied   : Boolean
  val description : String

  /** The timeout a ConditionWaiter waits for this particular condition */
  def timeout   : FiniteDuration = defaultTimeout
  def interval  : FiniteDuration = defaultInterval

  lazy val config = {
    val config = ConfigFactory.load()
    config.getConfig("de.wayofquality.blended.itestsupport.condition")
  }

  override def toString = s"Condition($description, $timeout)"

  private[this] def defaultTimeout = config.getLong("defaultTimeout").millis
  private[this] def defaultInterval = config.getLong("checkfrequency").millis
}
