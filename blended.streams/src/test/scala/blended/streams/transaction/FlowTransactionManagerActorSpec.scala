package blended.streams.transaction

import java.io.File

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

import scala.util.Success

class FlowTransactionManagerActorSpec extends TestKit(ActorSystem("TMgrActor"))
  with LoggingFreeSpecLike
  with Matchers
  with PropertyChecks {

  private val log : Logger = Logger[FlowTransactionManagerActorSpec]
  "The Transaction Manager Actor should" - {

    "process transaction update events and pass the response to a given actor" in {

      val fileMgr : FlowTransactionManager = new FileFlowTransactionManager(new File(BlendedTestSupport.projectTestOutput, "mgrActorSpec"))
      val mgr : ActorRef = system.actorOf(FlowTransactionManagerActor.props(fileMgr))
      val probe : TestProbe = TestProbe()

      forAll(FlowTransactionGen.genTrans){ t =>
        val event : FlowTransactionEvent = FlowTransactionStarted(t.tid, t.creationProps)
        mgr.tell((event, probe.ref), ActorRef.noSender)

        probe.expectMsgPF(){
          case Success(e) if e.isInstanceOf[FlowTransaction] =>
            val t2 : FlowTransaction = e.asInstanceOf[FlowTransaction]
            log.info(s"$t2")
            assert(t2.state == FlowTransactionStateStarted)
            assert(t2.tid === t.tid)
        }
      }
    }
  }
}
