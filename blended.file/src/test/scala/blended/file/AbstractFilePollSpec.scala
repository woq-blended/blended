package blended.file

import java.io.{File, FileOutputStream}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import blended.akka.SemaphoreActor
import org.apache.commons.io.FileUtils
import org.scalatest.Matchers

import scala.concurrent.duration._

trait AbstractFilePollSpec { this : Matchers =>

  def handler()(implicit system : ActorSystem) : FilePollHandler

  def genFile(f: File) : Unit = {
    val os = new FileOutputStream(f)
    os.write("Hallo Andreas".getBytes())
    os.flush()
    os.close()
  }

  protected def withMessages(dir : String, msgCount : Int)(implicit system : ActorSystem) : List[File] = {

    val sem : ActorRef = system.actorOf(Props[SemaphoreActor])

    val srcDir = new File(System.getProperty("projectTestOutput") + "/" + dir)
    FileUtils.deleteDirectory(srcDir)
    srcDir.mkdirs()

    val cfg = FilePollConfig(system.settings.config.getConfig("blended.file.poll")).copy(
      sourceDir = srcDir.getAbsolutePath()
    )

    val probe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[FileProcessed])

    val actor = system.actorOf(FilePollActor.props(cfg, handler(), Some(sem)))

    val files : List[File] = (1.to(msgCount)).map { i =>
      val f = new File(srcDir, s"test$i." + (if (i % 2 == 0) "txt" else "xml"))
      genFile(f)
      f
    }.toList

    val result : List[File] = files.filter(_.getName.endsWith("txt"))
    val processCount : Int = result.size

    val processed : List[FileProcessed] = probe.receiveWhile[FileProcessed](max = 10.seconds, messages = msgCount) {
      case fp : FileProcessed => fp
    }.toList

    files.forall{ f => (f.getName().endsWith("txt") && !f.exists()) || (!f.getName().endsWith("txt") && f.exists()) } should be (true)

    val names : List[String] = files.map(_.getName())

    processed should have size(processCount)
    assert(
      processed.forall(p => names.contains(p.cmd.f.getName()))
    )

    system.stop(actor)

    result
  }
}
