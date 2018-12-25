package blended.file

import java.io.File

import akka.actor.ActorSystem
import akka.util.ByteString
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.FlowHeaderConfig
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class FileDropSpec extends LoggingFreeSpec
  with Matchers
  with FileEnvelopeHeader
  with FileTestSupport {

  private val log : Logger = Logger[FileDropSpec]
  private implicit val system : ActorSystem = ActorSystem(getClass().getSimpleName())
  private val to : FiniteDuration = 1.second

  private val headerCfg = FlowHeaderConfig(prefix = "App")

  private val dropCfg : FileDropConfig = FileDropConfig(
    dirHeader = "",
    fileHeader = fileHeader(headerCfg.prefix),
    compressHeader = compressHeader(headerCfg.prefix),
    appendHeader = appendHeader(headerCfg.prefix),
    charsetHeader = charsetHeader(headerCfg.prefix),
    defaultDir = System.getProperty("projectTestOutput", "/tmp"),
    dropTimeout = to
  )

  val prepareDropper : FileDropConfig => String => FileDropConfig =  cfg => subDir => {
    val dir = cfg.defaultDir + "/" + subDir
    cleanUpDirectory(dir)
    cfg.copy(defaultDir = dir)
  }

  private def dropFile(dropper : EnvelopeFileDropper, cfg : FileDropConfig, env : FlowEnvelope) : Try[FileDropResult] = Try {
    val f = dropper.dropEnvelope(env)
    val r = Await.result(f, to)
    r
  }

  "The Envelope File dropper should" - {

    "drop an uncompressed file into a given directory" in {

      val cfg = prepareDropper(dropCfg)("drop")

      val dropper : EnvelopeFileDropper = new EnvelopeFileDropper(cfg, headerCfg, log)

      val env : FlowEnvelope = FlowEnvelope(
        FlowMessage("Hello Blended")(FlowMessage.props(
          dropCfg.fileHeader -> "test.txt"
        ).get)
      )

      val r : FileDropResult = dropFile(dropper, cfg, env).get
      r.error should be (None)

      verifyTargetFile(new File(cfg.defaultDir, "test.txt"), ByteString("Hello Blended"))
    }
  }

  "create a duplicate file if the file already exists in the target directory (without append)" in {

    val cfg : FileDropConfig = prepareDropper(dropCfg)("overwrite")
    val dropper : EnvelopeFileDropper = new EnvelopeFileDropper(cfg, headerCfg, log)

    val content : ByteString = ByteString("Hello Blended")

    val env : FlowEnvelope = FlowEnvelope(
      FlowMessage(content)(FlowMessage.props(
        cfg.fileHeader -> "test.txt"
      ).get)
    )

    dropFile(dropper, cfg, env)
    dropFile(dropper, cfg, env)

    val dups = getFiles(cfg.defaultDir, filter = duplicateFilter)
    dups should have size 1

    val files = getFiles(cfg.defaultDir, acceptAllFilter)
    files should have size 2

    files.forall { f => verifyTargetFile(f, content) } should be(true)
  }

  "append the content to the existing file if the append header is set to true" in {

    val cfg : FileDropConfig = prepareDropper(dropCfg)("append")
    val dropper : EnvelopeFileDropper = new EnvelopeFileDropper(cfg, headerCfg, log)
    val content : ByteString = ByteString("Hello Blended" * 10000)
    val zipContent : ByteString = zipCompress(content)

    val n : Int = 50

    val env : FlowEnvelope = FlowEnvelope(FlowMessage(zipContent)(FlowMessage.props(
      cfg.fileHeader -> "header.txt",
      cfg.compressHeader -> true,
      cfg.appendHeader -> true
    ).get))

    val env2 = FlowEnvelope(FlowMessage(content)(FlowMessage.props(
      cfg.fileHeader -> "header.txt",
      cfg.compressHeader -> true,
      cfg.appendHeader -> true
    ).get))

    1.to(n).foreach { _ =>
      dropFile(dropper, cfg, env)
    }

    val err : FileDropResult = dropFile(dropper, cfg, env2).get
    err.error should be (defined)

    val files = getFiles(cfg.defaultDir, acceptAllFilter)
    files should have size 1

    val expected : ByteString = multiply(content, n)
    files.forall { f => verifyTargetFile(f, expected) } should be(true)
  }

  "extract the (ZIP) compressed content if the compress header is set" in {

    val cfg : FileDropConfig = prepareDropper(dropCfg)("zipped")
    val dropper : EnvelopeFileDropper = new EnvelopeFileDropper(cfg, headerCfg, log)
    val content : ByteString = ByteString("Hello Blended")
    val zipContent : ByteString = zipCompress(content)

    val env : FlowEnvelope = FlowEnvelope(FlowMessage(zipContent)(FlowMessage.props(
      cfg.fileHeader -> "header.txt",
      cfg.compressHeader -> "true",
      cfg.appendHeader -> "true"
    ).get))

    val r : FileDropResult = dropFile(dropper, cfg, env).get
    r.error should be (empty)

    val files : List[File] = getFiles(cfg.defaultDir, acceptAllFilter)
    files should have size 1

    files.forall { f => verifyTargetFile(f, content) } should be(true)
  }

  "extract the (GZIP) compressed content if the compress header is set" in {

    val cfg : FileDropConfig = prepareDropper(dropCfg)("gzipped")
    val dropper : EnvelopeFileDropper = new EnvelopeFileDropper(cfg, headerCfg, log)
    val content : ByteString = ByteString("Hello Blended")
    val zipContent : ByteString = gzipCompress(content)

    val env : FlowEnvelope = FlowEnvelope(FlowMessage(zipContent)(FlowMessage.props(
      cfg.fileHeader -> "header.txt",
      cfg.compressHeader -> true,
      cfg.appendHeader -> true
    ).get))

    val r : FileDropResult = dropFile(dropper, cfg, env).get
    r.error should be (empty)

    val files : List[File] = getFiles(cfg.defaultDir, acceptAllFilter)
    files should have size 1

    files.forall { f => verifyTargetFile(f, content) } should be(true)
  }
}
