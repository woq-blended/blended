package blended.jms.utils

import java.util.concurrent.atomic.AtomicBoolean
import java.util.{Timer, TimerTask}

import javax.jms.ConnectionFactory

class PollingJMSReceiver(
  cf : ConnectionFactory,
  destName : String,
  interval : Int,
  receiveTimeout : Long,
  msgHandler : JMSMessageHandler,
  errorHandler : JMSErrorHandler,
  subscriptionName : Option[String] = None
) extends JMSSupport {

  private[this] val timer : Timer = new Timer()
  private[this] val stopping : AtomicBoolean = new AtomicBoolean(false)

  def start() : Unit = {
    stopping.set(false)
    poll()
  }

  def stop() : Unit = {
    stopping.set(true)
  }

  private[this] def poll() : Unit = {
    if (!stopping.get()) {
      timer.purge()
      // Get as many messages as possible
      receiveMessage(
        cf = cf,
        destName = destName,
        msgHandler = msgHandler,
        errorHandler = errorHandler,
        maxMessages = 0,
        receiveTimeout = receiveTimeout,
        subscriptionName = subscriptionName
      )
      timer.schedule(
        new TimerTask {
          override def run() : Unit = poll()
        }, interval * 1000l
      )
    }
  }
}
