package blended.streams.transaction.internal

import java.io.{BufferedWriter, File}
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.Date

import akka.NotUsed
import akka.stream.scaladsl.Source
import blended.streams.transaction.{FlowTransaction, FlowTransactionEvent, FlowTransactionManager}
import blended.util.logging.Logger
import prickle._
import blended.streams.json.PrickleProtocol._

import scala.io.BufferedSource
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FileFlowTransactionManager(dir: File) extends FlowTransactionManager {

  private val log : Logger = Logger[FileFlowTransactionManager]
  private val extension : String = "json"
  private val charset : Charset = Charset.forName("UTF-8")

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
      val updated: FlowTransaction = (findTransaction(e.transactionId).get match {
        case None =>
          val now: Date = new Date()
          log.debug(s"Storing new transaction [${e.transactionId}]")
          FlowTransaction(
            id = e.transactionId,
            created = now,
            lastUpdate = now,
            creationProps = e.properties
          )
        case Some(r) => r
      }).updateTransaction(e).get

      store(updated).get
    }
  }

  /**
    * @inheritdoc
    */
  override def findTransaction(tid: String): Try[Option[FlowTransaction]] = Try {

    measureDuration(s"Excuted find for [$tid] in "){ () =>
      log.debug(s"Trying to find transaction [$tid]")

      val tFile : File = new File(dir, s"$tid.$extension")

      if (tFile.exists()) {
        val json : String = load(tFile).get
        val t : FlowTransaction = Unpickle[FlowTransaction].fromString(json).get
        Some(t)
      } else {
        None
      }
    }
  }

  /**
    * @inheritdoc
    */
  override def removeTransaction(tid: String): Try[Option[FlowTransaction]] = Try {
    findTransaction(tid).get.map{ t =>
      val tFile : File = new File(dir, s"${t.tid}.$extension")

      if (tFile.delete()) {
        log.debug(s"deleted transaction file [${tFile.getAbsolutePath()}]")
      } else {
        log.warn(s"Failed to delete transaction file [${tFile.getAbsolutePath()}]")
      }

      t
    }
  }

  /**
    * @inheritdoc
    */
  override def transactions: Source[FlowTransaction, NotUsed] =
    Source.empty[FlowTransaction]

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
      case NonFatal(t) =>
        log.warn(s"Error writing transaction file [${tFile.getAbsolutePath()}][${t.getMessage()}]")
        throw t
    } finally {
      writer.foreach { w =>
        try {
          w.close()
        } catch {
          case NonFatal(t) =>
            log.warn(s"Error closing file [${tFile.getAbsolutePath()}][${t.getMessage()}]")
        }
      }
    }
  }

  private def load(f : File) : Try[String] = {
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
          log.warn(s"Error closing file [${f.getAbsolutePath()}]")
      }
    }
  }

  private def measureDuration[T](logMsg : String)(f: () => T) : T = {
    val start : Long = System.currentTimeMillis()
    val result : T = f()
    log.debug(logMsg + s"[${System.currentTimeMillis() - start}]ms")
    result
  }
}
