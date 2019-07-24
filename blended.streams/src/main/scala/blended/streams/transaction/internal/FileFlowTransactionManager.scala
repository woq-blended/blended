package blended.streams.transaction.internal

import java.io.{BufferedWriter, File}
import java.nio.charset.Charset
import java.nio.file.{DirectoryStream, Files, Path}
import java.util.Date
import scala.collection.JavaConverters._

import blended.streams.json.PrickleProtocol._
import blended.streams.transaction._
import blended.util.logging.Logger
import prickle._

import scala.io.BufferedSource
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object FileFlowTransactionManager {
  def apply(dir : File) : FileFlowTransactionManager = new FileFlowTransactionManager(
    FlowTransactionManagerConfig(dir)
  )
}

class FileFlowTransactionManager(
  override val config: FlowTransactionManagerConfig
) extends FlowTransactionManager {

  private val log : Logger = Logger[FileFlowTransactionManager]
  private val extension : String = "json"
  private val charset : Charset = Charset.forName("UTF-8")

  private val dir : File = config.dir

  private lazy val initialized : Boolean = {
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        log.warn(s"Unable to create directory [${dir.getAbsolutePath()}]")
      } else {
        log.info(s"Created directory [${dir.getAbsolutePath()}] to persist FlowTransactions")
      }
    }

    dir.exists() && dir.canRead() && dir.canWrite() && dir.isDirectory()
  }

  require(initialized)

  /**
    * @inheritdoc
    */
  override def updateTransaction(e: FlowTransactionEvent): Try[FlowTransaction] = Try {

    measureDuration(s"Transaction update for [${e.transactionId}] took ") { () =>
      val updated : FlowTransaction = findTransaction(e.transactionId).get match {
        case None =>
          val now: Date = new Date()
          log.trace(s"Storing new transaction [${e.transactionId}]")

          val newT : FlowTransaction = FlowTransaction(
            id = e.transactionId,
            created = now,
            lastUpdate = now,
            creationProps = e.properties
          )

          // if the event was not a started event we need to apply it
          if (e.state == FlowTransactionStateStarted) {
            newT
          } else {
            newT.updateTransaction(e)
          }

        case Some(r) => r.updateTransaction(e)
      }

      store(updated).get
    }
  }

  /**
    * @inheritdoc
    */
  override def findTransaction(tid: String): Try[Option[FlowTransaction]] = Try {

    measureDuration(s"Executed find for [$tid] in "){ () =>
      log.trace(s"Trying to find transaction [$tid]")

      val tFile : File = new File(dir, s"$tid.$extension")
      loadTransaction(tFile).get
    }
  }

  /**
    * @inheritdoc
    */
  override def removeTransaction(tid: String): Unit = Try {
    val tFile : File = new File(dir, s"${tid}.$extension")

    if (tFile.delete()) {
      log.trace(s"Deleted transaction file [${tFile.getAbsolutePath()}]")
    } else {
      log.warn(s"Failed to delete transaction file [${tFile.getAbsolutePath()}]")
    }
  }

  /**
    * @inheritdoc
    */
  override def transactions: Iterator[FlowTransaction] = {
    val dirStream : DirectoryStream[Path] = Files.newDirectoryStream(dir.toPath())
    dirStream.iterator().asScala
      .map{ p => loadTransaction(p.toFile()) }
      .filter(_.isSuccess)
      .map(_.get)
      .filter(_.isDefined)
      .map(_.get)
  }

  private def store(t : FlowTransaction) : Try[FlowTransaction] = Try {

    val json : String = Pickle.intoString(t)
    val tFile : File = new File(dir, s"${t.tid}.$extension")

    if (log.isTraceEnabled) {
      if (tFile.exists()) {
        log.trace(s"Replacing transaction file [${tFile.getAbsolutePath()}]")
      } else {
        log.trace(s"Creating transaction file [${tFile.getAbsolutePath()}]")
      }
    }

    var writer : Option[BufferedWriter] = None

    try {
      writer = Some(Files.newBufferedWriter(tFile.toPath(), charset))
      writer.foreach{ w => w.write(json) }
      t
    } catch {
      case NonFatal(e) =>
        log.warn(s"Error writing transaction file [${tFile.getAbsolutePath()}][${e.getMessage()}]")
        throw e
    } finally {
      writer.foreach { w =>
        try {
          w.close()
        } catch {
          case NonFatal(e) =>
            log.warn(s"Error closing file [${tFile.getAbsolutePath()}][${e.getMessage()}]")
        }
      }
    }
  }

  private def loadTransaction(f : File) : Try[Option[FlowTransaction]] = Try {
    if (f.exists()) {
      val json : String = loadFile(f).get
      val t : FlowTransaction = Unpickle[FlowTransaction].fromString(json).get
      Some(t)
    } else {
      None
    }
  }

  private def loadFile(f : File) : Try[String] = {
    val src : BufferedSource = scala.io.Source.fromFile(f, "UTF-8")

    try {
      Success(src.getLines().mkString("\n"))
    } catch {
      case NonFatal(t) =>
        log.warn(s"Exception encountered accessing transaction file [${f.getAbsolutePath()}]")
        Failure(t)
    } finally {
      try {
        src.close()
      } catch {
        case NonFatal(t) =>
          log.warn(s"Error closing file [${f.getAbsolutePath()}]:[${t.getMessage()}]")
      }
    }
  }

  private def measureDuration[T](logMsg : String)(f: () => T) : T = {
    val start : Long = System.currentTimeMillis()
    val result : T = f()
    log.trace(logMsg + s"[${System.currentTimeMillis() - start}]ms")
    result
  }
}
