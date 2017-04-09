package blended.jms.sampler.internal

import java.util.concurrent.atomic.AtomicBoolean
import javax.jms.ConnectionFactory

import akka.actor.ActorRef
import blended.akka.OSGIActorConfig
import org.slf4j.LoggerFactory

case class JmsSampler(cfg: OSGIActorConfig, cf: ConnectionFactory) extends JmsSamplerMBean {

  private[this] var encoding = "UTF-8"
  private[this] var destinationName : String = ""
  private[this] var worker : Option[ActorRef] = None
  private[this] val sampling : AtomicBoolean = new AtomicBoolean(false)

  private[this] val log = LoggerFactory.getLogger(classOf[JmsSampler])

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

  def setSampling(s : Boolean) : Unit = sampling.set(s)

  override def isSampling(): Boolean = sampling.get()

  override def startSampling(): Unit = if (!isSampling()) {

    if (destinationName.length() > 0) {
      log.info(s"Starting topic sampler for destination [${destinationName}]")
      worker = Some(cfg.system.actorOf(JMSSampleControlActor.props(cfg, cf, this)))
    }
  }

  override def stopSampling(): Unit = if (isSampling()) {
    log.info(s"Stopping topic sampler for destination [$destinationName]")
    worker.foreach(_ ! StopSampling)
    worker = None
  }
}
