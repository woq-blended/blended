package blended.jms.utils

import java.util.{Timer, TimerTask}
import java.util.concurrent.atomic.AtomicBoolean
import javax.jms.ConnectionFactory

class PollingJMSReceiver(
  cf: ConnectionFactory,
  destName : String,
  interval: Long,
  msgHandler: JMSMessageHandler
) extends JMSSupport {

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
      receiveMessage(cf, destName, msgHandler)
      new Timer().schedule(
        new TimerTask {
          override def run(): Unit = poll()
        }, interval
      )
    }
  }
}
