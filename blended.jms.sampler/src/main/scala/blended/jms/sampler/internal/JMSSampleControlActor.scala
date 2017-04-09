package blended.jms.sampler.internal

import java.io.File
import javax.jms.ConnectionFactory

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import blended.akka.OSGIActorConfig

object JMSSampleControlActor {

  def props(cfg: OSGIActorConfig, cf: ConnectionFactory, sampler: JmsSampler) =
    Props(new JMSSampleControlActor(cfg, cf, sampler))
}

class JMSSampleControlActor(cfg: OSGIActorConfig, cf: ConnectionFactory, sampler: JmsSampler) extends Actor with ActorLogging {

  override def preStart(): Unit = self ! Init

  case object Init

  override def receive: Receive = {
    case Init =>

      val dir : File = {
        val f = new File(cfg.idSvc.getContainerContext().getContainerLogDirectory() + "/../trace")
        if (!f.exists()) f.mkdirs()
        f.getAbsoluteFile()
      }

      val destName = sampler.getDestinationName()
      val sampleActor = context.actorOf(JMSSampleActor.props(dir, cfg, cf, destName, sampler.getEncoding()))
      sampleActor ! StartSampling
      sampler.setSampling(true)

      context.watch(sampleActor)
      context.become(sampling(sampleActor, destName))

      log.debug(s"Topic sampler [${destName}] started")
  }

  def sampling(sampleActor: ActorRef, destName: String) : Receive ={
    case StopSampling => sampleActor ! StopSampling
    case Terminated(_) =>
      log.debug(s"Topic sampler [${sampler}] terminated")
      context.stop(self)
      sampler.setSampling(false)
  }
}
