package blended.streams.transaction

import java.io.File

import com.typesafe.config.Config
import blended.util.config.Implicits._
import scala.concurrent.duration._

object FlowTransactionManagerConfig {

  val defaultRetainStale : FiniteDuration = 10.days
  val defaultRetainCompleted : FiniteDuration = 1.hour
  val defaultRetainFailed : FiniteDuration = 7.days

  def fromConfig(baseDir : File, cfg : Config) : FlowTransactionManagerConfig = {
    val dir : String = cfg.getString("directory", "transactions")
    val retainStale = cfg.getDuration("retainStale", defaultRetainStale)
    val retainFailed = cfg.getDuration("retainFailed", defaultRetainFailed)
    val retainCompleted = cfg.getDuration("retainCompleted", defaultRetainCompleted)

    FlowTransactionManagerConfig(
      dir = new File(baseDir, dir),
      retainStale = retainStale,
      retainCompleted = retainCompleted,
      retainFailed = retainFailed
    )
  }
}

case class FlowTransactionManagerConfig(
  dir : File,
  retainStale : FiniteDuration = FlowTransactionManagerConfig.defaultRetainStale,
  retainCompleted : FiniteDuration = FlowTransactionManagerConfig.defaultRetainCompleted,
  retainFailed : FiniteDuration = FlowTransactionManagerConfig.defaultRetainFailed
)
