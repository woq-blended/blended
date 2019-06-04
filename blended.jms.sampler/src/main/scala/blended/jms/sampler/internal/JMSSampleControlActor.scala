package blended.jms.sampler.internal

import java.io.File

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import blended.akka.OSGIActorConfig
import javax.jms.ConnectionFactory

object JMSSampleControlActor {

  def props(cfg : OSGIActorConfig, cf : ConnectionFactory, sampler : JmsSampler) =
    Props(new JMSSampleControlActor(cfg, cf, sampler))
}

class JMSSampleControlActor(cfg : OSGIActorConfig, cf : ConnectionFactory, sampler : JmsSampler) extends Actor with ActorLogging {

  override def supervisorStrategy : SupervisorStrategy = OneForOneStrategy() {
    case e : Exception =>
      log.warning(s"Topic Sampler terminated with exception [${e.getMessage}]")
      Stop
  }

  override def preStart() : Unit = self ! Init

  case object Init

  override def receive : Receive = {
    case Init =>

      val dir : File = {
        val f = new File(cfg.idSvc.containerContext.getContainerLogDirectory() + "/../trace")
        if (!f.exists()) f.mkdirs()
        f.getAbsoluteFile()
      }

      val destName = sampler.getDestinationName()
      val sampleActor = context.actorOf(JMSSampleActor.props(dir, cfg, cf, destName, sampler.getEncoding()))
      context.watch(sampleActor)

      sampleActor ! StartSampling
      sampler.setSampling(true)

      context.become(sampling(sampleActor, destName))

      log.debug(s"Topic sampler [${destName}] started")
  }

  def sampling(sampleActor : ActorRef, destName : String) : Receive = {
    case StopSampling => sampleActor ! StopSampling
    case Terminated(_) =>
      log.debug(s"Topic sampler [${sampler}] terminated")
      context.stop(self)
      sampler.setSampling(false)
  }
}
