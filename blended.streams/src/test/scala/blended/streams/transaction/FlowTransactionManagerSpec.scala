package blended.streams.transaction

import java.io.File

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import blended.akka.internal.BlendedAkkaActivator
import blended.persistence.PersistenceService
import blended.persistence.h2.internal.H2Activator
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty}
import blended.streams.processor.{CollectingActor, Collector}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.{BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class FlowTransactionManagerSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with BeforeAndAfterAll
  with PojoSrTestHelper {

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.persistence.h2" -> new H2Activator()
  )

  private val log = Logger[FlowTransactionManagerSpec]

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  private implicit val timeout = 5.seconds
  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)

  private val pSvc : PersistenceService = mandatoryService[PersistenceService](registry)(None)

  private val mgr : ActorRef  = system.actorOf(FlowTransactionManager.props(pSvc))

  private def transaction(mgr : ActorRef, id : String)(implicit timeout: Timeout) : Future[FlowTransaction] = {
    log.debug(s"Getting transaction state [$id] from [${mgr.path}]")
    (mgr ? FlowTransactionActor.TransactionState(id)).mapTo[FlowTransaction]
  }

  private def singleTest(event : FlowTransactionEvent)(f : List[FlowTransaction] => Unit): Unit = {

    implicit val eCtxt : ExecutionContext = system.dispatcher

    val coll = Collector[FlowTransaction]("trans")
    mgr.tell(event, coll.actor)

    akka.pattern.after(2.seconds, system.scheduler)( Future {
      coll.actor ! CollectingActor.Completed
    })

    val result = coll.result.map{ l => f(l) }

    Await.result(result, 3.seconds)
    system.stop(coll.actor)
  }

  override protected def afterAll(): Unit = Await.result(system.terminate(), 3.seconds)

  "The transaction manager should" - {

    "create a new transaction for a Transaction Started event" in {

      val env = FlowEnvelope(FlowMessage.noProps).withHeader("foo", "bar").get

      singleTest(FlowTransaction.startEvent(Some(env))) { l =>
        l should have size 1
        val t = l.head
        t.tid should be (env.id)
        t.creationProps.get("foo") should be (Some(MsgProperty("bar")))
      }
    }

    "maintain the state across transaction manager restarts" in {

      implicit val timeout : Timeout = Timeout(3.seconds)

      val env = FlowEnvelope(FlowMessage.noProps).withHeader("foo", "bar").get

      singleTest(FlowTransaction.startEvent(Some(env))){ l =>
        l should have size 1
        l.head.tid should be (env.id)
        l.head.creationProps.get("foo") should be (Some(MsgProperty("bar")))
      }

      system.stop(mgr)

      val mgr2 = system.actorOf(FlowTransactionManager.props(pSvc))
      val t2 = Await.result(transaction(mgr2, env.id), 3.seconds)

      t2.tid should be (env.id)
      t2.creationProps.get("foo") should be (Some(MsgProperty("bar")))

      system.stop(mgr2)
    }
  }

}
