package blended.jms.utils

import java.util.concurrent.atomic.AtomicBoolean
import java.util.{Timer, TimerTask}
import javax.jms.ConnectionFactory

class PollingJMSReceiver(
  cf: ConnectionFactory,
  destName : String,
  interval: Long,
  msgHandler: JMSMessageHandler,
  subscriptionName : Option[String] = None
) extends JMSSupport {

  private[this] val timer : Timer = new Timer()
  private[this] val stopping : AtomicBoolean = new AtomicBoolean(false)

  def start(): Unit = {
    stopping.set(false)
    poll()
  }

  def stop(): Unit = {
    stopping.set(true)
  }

  private[this] def poll() : Unit = {
    if (!stopping.get()) {
      timer.purge()
      // Get as many messages as possible
      receiveMessage(cf, destName, msgHandler, 0, subscriptionName)
      timer.schedule(
        new TimerTask {
          override def run(): Unit = poll()
        }, interval
      )
    }
  }
}