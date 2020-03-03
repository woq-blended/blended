package blended.streams.file

import java.io.File

import akka.NotUsed
import akka.stream.scaladsl.Source
import blended.streams.StreamFactories
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{AckProcessor, Collector}
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@RequiresForkedJVM
class ParallelFileSourceSpec extends AbstractFileSourceSpec {

  "The FilePollSource should" - {

    "allow to FileAckSources to process files in parallel" in {

      val numSrc : Int = 5
      val numMsg : Int = 5000
      val t : FiniteDuration = 5.seconds

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, ctCtxt)
        .copy(sourceDir = BlendedTestSupport.projectTestOutput + "/parallel" )

      def countWords(l : Seq[String]) : Map[String, Int] = l.foldLeft(Map.empty[String, Int]){ (current, s) =>
        current.get(s) match {
          case None => current + (s -> 1)
          case Some(v) => current.filterKeys(_ != s) + (s -> (v + 1))
        }
      }

      def createCollector(subId : Int, startDelay : Option[FiniteDuration] = None) : Collector[FlowEnvelope] = {
        val src : Source[FlowEnvelope, NotUsed] =
          Source.fromGraph(new FileAckSource(
            pollCfg.copy(id = s"poller$subId", interval = 100.millis), envLogger
          )).async.via(new AckProcessor(s"simplePoll$subId.ack").flow)

        startDelay.foreach(d => Thread.sleep(d.toMillis))
        StreamFactories.runSourceWithTimeLimit(s"parallel${subId}", src, Some(t))
      }

      prepareDirectory(pollCfg.sourceDir)
      1.to(numMsg).foreach{ i => genFile(new File(pollCfg.sourceDir, s"test_$i.txt")) }

      val results : Seq[Future[List[FlowEnvelope]]] = 0.until(numSrc).map{ i=>
        val coll : Collector[FlowEnvelope] = createCollector(i, if (i == 0) None else Some(20.millis))
        coll.result
      }

      val combined : Future[Seq[List[FlowEnvelope]]] = Future.sequence(results)
      val allResults : Seq[String] = Await.result(combined, t + 1.second).flatten.map(_.header[String]("BlendedFileName").get)

      val dups : Map[String, Int] = countWords(allResults).filter{ case (_, v) => v > 1 }
      dups should be (empty)

      assert(allResults.size == numMsg)

      getFiles(dirName = pollCfg.sourceDir, pattern = ".*", recursive = false) should be (empty)
    }
  }
}
