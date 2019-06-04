package blended.jms.sampler.internal

import java.io.{ByteArrayOutputStream, File}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import blended.akka.OSGIActorConfig
import blended.util.FileHelper
import javax.jms._

case object StartSampling
case object StopSampling
case class MsgReceived(m : Message)

object JMSSampleActor {

  def props(dir : File, cfg : OSGIActorConfig, cf : ConnectionFactory, destName : String, encoding : String) : Props =
    Props(new JMSSampleActor(dir, cfg, cf, destName, encoding))
}

class JMSSampleActor(dir : File, cfg : OSGIActorConfig, cf : ConnectionFactory, destName : String, encoding : String) extends Actor with ActorLogging {

  private[this] val count = new AtomicLong(0)
  private[this] val df = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS")

  override def receive : Receive = {

    case StartSampling =>
      val con = cf.createConnection()
      con.start()
      val session = con.createSession(false, Session.AUTO_ACKNOWLEDGE)
      log.info(s"Creating sampling consumer on topic [$destName]")

      val t = session.createTopic(destName)
      val cons = session.createConsumer(t)
      cons.setMessageListener(new SampleListener(self))

      context.become(sampling(con, session, cons).orElse(lifeCycle(con, session, cons)))

    case StopSampling =>
      context.stop(self)

    case MsgReceived(_) => // do nothing
  }

  def sampling(con : Connection, session : Session, cons : MessageConsumer) : Receive = {

    case MsgReceived(msg) =>
      log.debug(s"Processing message from topic [$destName]")
      val writer = context.actorOf(MsgWriteActor.props(dir, msg))
      context.watch(writer)
      context.become(busy(con, session, cons).orElse(lifeCycle(con, session, cons)))
  }

  def busy(con : Connection, session : Session, cons : MessageConsumer) : Receive = {
    case MsgReceived(_) => log.debug(s"Ignoring message from topic [$destName]")
    case Terminated(_)  => context.become(sampling(con, session, cons).orElse(lifeCycle(con, session, cons)))
  }

  def lifeCycle(con : Connection, session : Session, cons : MessageConsumer) : Receive = {
    case StartSampling => // do nothing
    case StopSampling =>
      log.debug(s"Stopping topic sampler for topic [$destName]")
      cons.close()
      session.close()
      con.close()
      context.stop(self)
  }

  private class SampleListener(sampler : ActorRef) extends MessageListener {
    override def onMessage(message : Message) : Unit = sampler ! MsgReceived(message)
  }

  object MsgWriteActor {
    def props(dir : File, msg : Message) : Props = Props(new MsgWriteActor(dir, msg))
  }
  class MsgWriteActor(dir : File, msg : Message) extends Actor with ActorLogging {

    case object WriteMsg

    def writeMsg(bytes : Array[Byte]) : Unit = {
      try {
        val fileName = s"$destName-${df.format(new Date())}-${count.incrementAndGet()}"
        val file = new File(dir, fileName)
        FileHelper.writeFile(file, bytes)
        log.info(s"Written [${bytes.length}] bytes to [${file.getAbsolutePath()}]")
      }
    }

    override def preStart() : Unit = self ! WriteMsg

    override def receive : Receive = {
      case WriteMsg =>
        msg match {
          case bMsg : BytesMessage =>
            val bytes = new Array[Byte](8192)
            val bos = new ByteArrayOutputStream()

            var bCnt = 0

            do {
              bCnt = bMsg.readBytes(bytes)
              if (bCnt > 0) bos.write(bytes, 0, bCnt)
            } while (bCnt > 0)

            bos.flush()
            bos.close()

            writeMsg(bos.toByteArray())

          case tMsg : TextMessage =>
            writeMsg(tMsg.getText().getBytes(encoding))

          case m : Message => log.info(s"Sampler does not process messages of type [${m.getClass().getSimpleName()}]")
        }

        context.stop(self)
    }
  }
}
