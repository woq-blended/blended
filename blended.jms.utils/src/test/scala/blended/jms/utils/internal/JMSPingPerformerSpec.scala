package blended.jms.utils.internal

import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Source
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import blended.jms.utils.{BlendedJMSConnectionConfig, ExecutePing, JMSSupport, PingFailed, PingResult, PingSuccess, PingTimeout}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.jms.{Connection, MessageProducer, Session}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

case class PingExecute(
  count : Long,
  con : Connection,
  cfg : BlendedJMSConnectionConfig,
  operations : PingOperations = new DefaultPingOperations()
)

class PingExecutor extends Actor {
  override def receive : Receive = {
    case exec : PingExecute =>
      val actor = context.actorOf(JmsPingPerformer.props(
        exec.cfg, exec.con, exec.operations
      ))

      context.watch(actor)

      actor ! ExecutePing(self, exec.count)
      context.become(executing(sender()))
  }

  def executing(requestor : ActorRef) : Receive = {
    case Terminated(_) => context.stop(self)
    case m             => requestor ! m
  }
}

abstract class JMSPingPerformerSpec extends TestKit(ActorSystem("JMSPingPerformer"))
  with LoggingFreeSpecLike
  with ImplicitSender
  with JMSSupport {

  private[this] val counter = new AtomicLong(0)
  private[this] implicit val materializer : Materializer = ActorMaterializer()

  val pingQueue : String
  val pingTopic : String

  val cfg : BlendedJMSConnectionConfig
  var con : Option[Connection]

  protected val bulkCount : Int = 40000
  protected val bulkTimeout : FiniteDuration = Math.max(1, bulkCount / 20000).minutes

  private[this] implicit val eCtxt : ExecutionContext = system.dispatcher

  private[this] def execPing(exec : PingExecute)(implicit to : Timeout) : Future[PingResult] = {
    (system.actorOf(Props[PingExecutor]) ? exec).mapTo[PingResult]
  }

  private[this] val pingSuccess : PartialFunction[Any, Boolean] = {
    case PingSuccess(_) => true
    case _              => false
  }

  private[this] val pingFailed : PartialFunction[Any, Boolean] = {
    case PingFailed(_) => true
    case _             => false
  }

  private[this] val failingInit = new DefaultPingOperations() {
    override def createProducer(s : Session, dest : String) : Try[MessageProducer] = Try {
      throw new Exception("failing")
    }
  }

  private[this] val timingOut = new DefaultPingOperations() {
    override def createProducer(s : Session, dest : String) : Try[MessageProducer] = {
      Thread.sleep(100)
      super.createProducer(s, dest)
    }
  }

  private[this] val failingProbe = new DefaultPingOperations() {

    override def probePing(info : PingInfo)(implicit eCtxt : ExecutionContext) : Future[PingResult] = Future {
      PingFailed(new Exception("Failed"))
    }
  }

  private[this] def threadCount() : Int = ManagementFactory.getThreadMXBean().getThreadCount

  "The JMSPingPerformer should " - {

    "perform a queue based ping" in {
      val result = Await.result(
        execPing(PingExecute(
          counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = s"queue:$pingQueue")
        ))(3.seconds), 3.seconds
      )

      assert(result.isInstanceOf[PingSuccess])
    }

    "perform a topic based ping" in {
      val result = Await.result(
        execPing(PingExecute(
          count = counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = s"topic:$pingTopic")
        ))(3.seconds), 3.seconds
      )
      assert(result.isInstanceOf[PingSuccess])
    }

    "respond with a negative ping result if the ping operation fails" in {
      val result = Await.result(
        execPing(PingExecute(
          count = counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = s"topic:$pingTopic"),
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
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = s"topic:$pingTopic", pingTimeout = 1.milli),
          operations = timingOut
        ))(3.seconds), 3.seconds
      )
      assert(result == PingTimeout)
    }

    "does not leak threads on successful pings" in {

      val threads : Int = threadCount()

      val src = Source(1.to(bulkCount)).map { _ : Int =>
        execPing(PingExecute(
          count = counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = s"topic:$pingTopic")
        ))(3.seconds)
      }

      val result = src.mapAsync(10)(i => i).runFold(true)((c, i) => c && i.isInstanceOf[PingSuccess])

      assert(Await.result(result, bulkTimeout))
      Thread.sleep(10000)
      assert(threadCount() <= threads + 128)
    }

    "does not leak threads on failed ping inits" in {

      val threads : Int = threadCount()

      val src = Source(1.to(bulkCount)).map { i: Int =>
        execPing(PingExecute(
          count = counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = s"topic:$pingTopic", pingTimeout = 50.millis),
          operations = timingOut
        ))(10.seconds)
      }

      val result = src.mapAsync(10)(i => i).runFold(true)((c, i) => c && i == PingTimeout)

      assert(Await.result(result, bulkTimeout * 2))
      Thread.sleep(10000)
      assert(threadCount() <= threads + 128)
    }

    "does not leak threads on failed ping probes" in {

      val threads : Int = threadCount()

      val src = Source(1.to(bulkCount)).map { _ : Int =>
        execPing(PingExecute(
          count = counter.incrementAndGet(),
          con = con.get,
          cfg = cfg.copy(clientId = "jmsPing", pingDestination = s"topic:$pingTopic"),
          operations = failingProbe
        ))(3.seconds)
      }

      val result : Future[(Int, Int)] = src.mapAsync(10)(i => i).runFold((0, 0)) {
        case ((otherCount, failedCount), i : PingFailed) => (otherCount, failedCount + 1)
        case ((otherCount, failedCount), i)              => (otherCount + 1, failedCount)
      }

      assert(Await.result(result, bulkTimeout) === (0, bulkCount))
      Thread.sleep(10000)
      assert(threadCount() <= threads + 128)
    }
  }
}
