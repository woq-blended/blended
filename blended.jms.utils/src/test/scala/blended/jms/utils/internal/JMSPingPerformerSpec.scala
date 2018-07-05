package blended.jms.utils.internal

import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import blended.jms.utils.{BlendedJMSConnectionConfig, JMSSupport}
import javax.jms.{Connection, MessageProducer, Session}
import org.scalatest.FreeSpecLike

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

case class PingExecute(
  count: Long,
  con: Connection,
  cfg: BlendedJMSConnectionConfig,
  operations: PingOperations = new DefaultPingOperations()
)

class PingExecutor extends Actor {
  override def receive: Receive = {
    case exec: PingExecute =>
      val actor = context.actorOf(JmsPingPerformer.props(
        exec.cfg, exec.con, exec.operations
      ))

      actor ! ExecutePing(self, exec.count)
      context.become(executing(sender()))
  }

  def executing(requestor: ActorRef) : Receive = {
    case m =>
      requestor ! m
      context.stop(self)
  }
}

abstract class JMSPingPerformerSpec extends TestKit(ActorSystem("JMSPingPerformer"))
  with FreeSpecLike
  with ImplicitSender
  with JMSSupport {

  private[this] val counter = new AtomicLong(0)
  private[this] implicit val materializer = ActorMaterializer()

  val cfg : BlendedJMSConnectionConfig
  var con : Option[Connection]

  val bulkCount : Int = 10000
  val bulkTimeout = Math.max(1, bulkCount / 100000).minutes

  private[this] implicit val eCtxt : ExecutionContext = system.dispatchers.lookup("FixedPool")

  private[this] def execPing(exec : PingExecute)(implicit to: Timeout) : Future[PingResult] = {
    (system.actorOf(Props[PingExecutor]) ? exec).mapTo[PingResult]
  }

  private[this] val pingSuccess : PartialFunction[Any, Boolean] = {
    case PingSuccess(_) => true
    case _ => false
  }

  private[this] val pingFailed : PartialFunction[Any, Boolean] = {
    case PingFailed(_) => true
    case _ => false
  }

  private[this] val failingInit = new DefaultPingOperations() {
    override def createProducer(s: Session, dest: String): Try[MessageProducer] = Try {
      throw new Exception("failing")
    }
  }

  private[this] val timingOut = new DefaultPingOperations() {
    override def createProducer(s: Session, dest: String): Try[MessageProducer] = {
      Thread.sleep(10.seconds.toMillis)
      super.createProducer(s, dest)
    }
  }

  private[this] val failingProbe = new DefaultPingOperations() {

    override def probePing(info: PingInfo)(implicit eCtxt: ExecutionContext): Future[Option[PingResult]] = Future {
      Some(PingFailed(new Exception("Failed")))
    }
  }

  private[this] def threadCount(): Int = ManagementFactory.getThreadMXBean().getThreadCount

  "The JMSPingPerformer should " - {

    "perform a queue based ping" in {
      val result = Await.result(
        execPing(PingExecute(
          counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = "queue:blendedPing")
        ))(3.seconds), 3.seconds
      )

      assert(result.isInstanceOf[PingSuccess])
    }

    "perform a topic based ping" in {
      val result = Await.result(
        execPing(PingExecute(
          count = counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = "topic:blendedPing")
        ))(3.seconds), 3.seconds
      )
      assert(result.isInstanceOf[PingSuccess])
    }

    "respond with a negative ping result if the ping operation fails" in {
      val result = Await.result(
        execPing(PingExecute(
          count = counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = "topic:blendedPing"),
          operations = failingInit
        ))(3.seconds), 3.seconds
      )
      assert(result.isInstanceOf[PingFailed])
    }

    "respond with a PingTimeout in case the ping takes too long" in {
      val result = Await.result(
        execPing(PingExecute(
          count = counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = "topic:blendedPing", pingTimeout = 1),
          operations = timingOut
        ))(3.seconds), 3.seconds
      )
      assert(result == PingTimeout)
    }

    "does not leak threads on successful pings" in {

      val src = Source(1.to(bulkCount)).map { i : Int  =>
        execPing(PingExecute(
          count = counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = "topic:blendedPing")
        ))(3.seconds)
      }

      val result = src.mapAsync(10)(i => i).runFold(true)( (c,i) => c && i.isInstanceOf[PingSuccess])

      assert(Await.result(result, bulkTimeout))
      Thread.sleep(10000)
      assert(threadCount() <= 100)
    }

    "does not leak threads on failed ping probes" in {

      val src = Source(1.to(bulkCount)).map { i : Int  =>
        execPing(PingExecute(
          count = counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = "topic:blendedPing"),
          operations = failingProbe
        ))(3.seconds)
      }

      val result = src.mapAsync(10)(i => i).runFold(true)( (c,i) => c && i.isInstanceOf[PingFailed])

      assert(Await.result(result, bulkTimeout))
      Thread.sleep(10000)
      assert(threadCount() <= 100)
    }
  }
}
