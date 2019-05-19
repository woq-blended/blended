package blended.streams.file

import java.io.{File, FileOutputStream}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Source
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.streams.StreamFactories
import blended.streams.message.FlowEnvelope
import blended.streams.processor.Collector
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import com.typesafe.config.Config
import org.apache.commons.io.FileUtils
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._

@RequiresForkedJVM
class FileSourceSpec extends SimplePojoContainerSpec
  with PojoSrTestHelper
  with LoggingFreeSpecLike
  with Matchers {

  override def baseDir: String = s"${BlendedTestSupport.projectTestOutput}/container"

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator()
  )

  implicit val timeout : FiniteDuration = 1.second
  private val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](registry)(None)
  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val materializer : Materializer = ActorMaterializer()

  private def prepareDirectory(subDir : String) : File = {

    val dir = new File(BlendedTestSupport.projectTestOutput, subDir)

    FileUtils.deleteDirectory(dir)
    dir.mkdirs()

    dir
  }

  private def genFile(f: File) : Unit = {
    val os = new FileOutputStream(f)
    os.write("Hallo Andreas".getBytes())
    os.flush()
    os.close()
  }

  "The FilePollSource should" - {

    val rawCfg : Config = idSvc.getContainerContext().getContainerConfig().getConfig("simplePoll")

    "perform a regular file poll from a given directory" in {

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(sourceDir = BlendedTestSupport.projectTestOutput + "/simplePoll" )

      val src : Source[FlowEnvelope, NotUsed] = Source.fromGraph(new FileAckSource(pollCfg))

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, timeout){ env => }

      val result : List[FlowEnvelope] = Await.result(collector.result, timeout + 100.millis)

      result should have size(1)
    }
  }
}
