package blended.streams.transaction

import java.io.File

import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.concurrent.duration._

object FlowTransactionManagerConfig {

  val defaultRetainStale : FiniteDuration = 10.days
  val defaultRetainCompleted : FiniteDuration = 1.hour
  val defaultRetainFailed : FiniteDuration = 7.days

  def fromConfig(baseDir : File, cfg : Option[Config]) : FlowTransactionManagerConfig = {

    cfg.map{ config =>
      val dir : String = config.getString("directory", "transactions")
      val retainStale = config.getDuration("retainStale", defaultRetainStale)
      val retainFailed = config.getDuration("retainFailed", defaultRetainFailed)
      val retainCompleted = config.getDuration("retainCompleted", defaultRetainCompleted)

      FlowTransactionManagerConfig(
        dir = if (dir.startsWith("/")) {
          new File(dir)
        } else {
          new File(baseDir, dir)
        },
        retainStale = retainStale,
        retainCompleted = retainCompleted,
        retainFailed = retainFailed
      )
    }.getOrElse(
      FlowTransactionManagerConfig(new File(baseDir, "transactions"))
    )
  }
}

case class FlowTransactionManagerConfig(
  dir : File,
  retainStale : FiniteDuration = FlowTransactionManagerConfig.defaultRetainStale,
  retainCompleted : FiniteDuration = FlowTransactionManagerConfig.defaultRetainCompleted,
  retainFailed : FiniteDuration = FlowTransactionManagerConfig.defaultRetainFailed
) {

  val cleanupInterval : FiniteDuration = Seq(
    retainStale, retainCompleted, retainCompleted
  ).min
}
