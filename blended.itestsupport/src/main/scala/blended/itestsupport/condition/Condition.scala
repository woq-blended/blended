package blended.itestsupport.condition

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import blended.itestsupport.condition.ConditionActor.{CheckCondition, ConditionCheckResult}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object Condition {

  def verify(cond : Condition)(implicit system : ActorSystem) : Try[Boolean] = Try {

    implicit val timeout : Timeout = Timeout(cond.timeout)

    val actor = system.actorOf(ConditionActor.props(cond))
    val result : ConditionCheckResult = Await.result((actor ? CheckCondition).mapTo[ConditionCheckResult], cond.timeout)

    result.allSatisfied
  }
}

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

  lazy val config : Config = {
    val config = ConfigFactory.load()
    config.getConfig("blended.itestsupport.condition")
  }

  override def toString: String = s"Condition($description, $timeout)"

  private[this] def defaultTimeout : FiniteDuration = config.getLong("defaultTimeout").millis
  private[this] def defaultInterval : FiniteDuration = config.getLong("checkfrequency").millis
}
