package blended.streams.file

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestProbe
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.processor.{AckProcessor, Collector}
import blended.streams.{FlowHeaderConfig, FlowProcessor, StreamFactories}
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, FileTestSupport}
import blended.util.logging.Logger
import com.typesafe.config.Config
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

abstract class AbstractFileSourceSpec extends SimplePojoContainerSpec
  with PojoSrTestHelper
  with LoggingFreeSpecLike
  with Matchers
  with FileTestSupport
  with FileSourceTestSupport {

  override def baseDir: String = s"${BlendedTestSupport.projectTestOutput}/container"

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator()
  )

  implicit val timeout : FiniteDuration = 1.second
  protected val ctCtxt : ContainerContext = mandatoryService[ContainerContext](registry)(None)
  protected implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  protected implicit val eCtxt : ExecutionContext = system.dispatcher
  protected implicit val materializer : Materializer = ActorMaterializer()
  protected val log : Logger = Logger[FileSourceSpec]
  protected val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(FlowHeaderConfig.create(ctCtxt), log)

  val rawCfg : Config = ctCtxt.containerConfig.getConfig("simplePoll")

  protected def testWithLock(srcDir : File, lockFile : File, pollCfg : FilePollConfig): Unit = {

    case class FilePolled(env : FlowEnvelope)

    def pollFiles(t : FiniteDuration) : List[FlowEnvelope] = {
      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg, envLogger))
          .via(FlowProcessor.fromFunction("event", envLogger){ env => Try {
            system.eventStream.publish(FilePolled(env))
            env
          }})
          .via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, Some(t))
      Await.result(collector.result, t + 100.millis)
    }

    val probe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[FilePolled])

    prepareDirectory(pollCfg.sourceDir)
    genFile(lockFile)
    akka.pattern.after(timeout + 200.millis, system.scheduler)(Future {
      log.info(s"Removing file [${lockFile.getAbsolutePath()}]")
      lockFile.delete()
    })

    genFile(new File(pollCfg.sourceDir, "test.txt"))

    // make sure we do not receive any messages before the lock file is removed
    probe.expectNoMessage(timeout)
    pollFiles(timeout * 3) should have size(1)
  }

}
