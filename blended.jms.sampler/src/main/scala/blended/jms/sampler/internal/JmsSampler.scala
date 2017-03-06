package blended.jms.sampler.internal

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.jms.ConnectionFactory

import blended.container.context.ContainerIdentifierService
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

class JmsSampler(idSvc: ContainerIdentifierService, cf: ConnectionFactory) extends JmsSamplerMBean {

  private[this] var encoding = "UTF-8"
  private[this] var destinationName : String = ""
  private[this] var worker : Option[TopicSampler] = None
  private[this] val stopPending : AtomicBoolean = new AtomicBoolean(false)
  private[this] var samplerThread : Option[Thread] = None

  private[this] val log = LoggerFactory.getLogger(classOf[JmsSampler])

  private[this] val dir = {
    val f = new File(idSvc.getContainerContext().getContainerLogDirectory() + "/../trace")
    if (!f.exists()) f.mkdirs()
    f.getAbsoluteFile()
  }

  override def getEncoding(): String = encoding

  override def setEncoding(newEncoding: String): Unit = encoding = newEncoding

  override def getDestinationName(): String = destinationName

  override def setDestinationName(newName : String): Unit = {
    if (isSampling()) {
      throw new Exception(s"Sampler is currently active on topic [$destinationName]. You have to stop it first.")
    } else {
      destinationName = newName.trim()
    }
  }

  override def isSampling(): Boolean = worker.isDefined

  override def startSampling(): Unit = if (!isSampling()) {

    if (destinationName.length() > 0) {
      worker = Some(new TopicSampler(dir, cf, destinationName.trim(), getEncoding()))
      try {
        worker.get.initSampler()
      } catch {
        case NonFatal(e) =>
          log.warn(s"Could not start sampler for destination [$destinationName] : [${e.getMessage()}]")
          stopSampling()
      }

      worker.foreach { ts =>
        samplerThread = Some(new Thread(new Runnable {
          override def run() = while (!stopPending.get()) {
            ts.sample()
          }
        }))
        log.info(s"Starting sampler for topic [$destinationName]")
        samplerThread.foreach(_.start())
      }
    }
  }

  override def stopSampling(): Unit = if (isSampling() && !stopPending.get()) {
    log.info(s"Stopping sampler for destination [$destinationName]")
    stopPending.set(true)
    worker.foreach(_.closeSampler())
    worker = None
    stopPending.set(false)
  }
}
