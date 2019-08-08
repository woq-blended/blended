package blended.streams

import scala.concurrent.duration.FiniteDuration

trait BlendedStreamsConfig {

  def transactionShard : Option[String]

  def minDelay : FiniteDuration
  def maxDelay : FiniteDuration
  def exponential : Boolean
  def random : Double
  def onFailureOnly : Boolean
  def resetAfter : FiniteDuration

  override def toString: String = s"${getClass().getName()}(shard=$transactionShard, minDelay=$minDelay, maxDelay=$maxDelay, " +
    s"exponential$exponential), random=$random, failureOnly=$onFailureOnly, resetAfter=$resetAfter)"
}

