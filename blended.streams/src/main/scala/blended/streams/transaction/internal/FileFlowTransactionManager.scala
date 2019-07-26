package blended.streams.transaction.internal

import java.io.{BufferedWriter, File}
import java.nio.charset.Charset
import java.nio.file.{DirectoryStream, Files, Path}
import java.util.Date

import blended.streams.json.PrickleProtocol._
import blended.streams.transaction._
import blended.util.logging.Logger
import prickle._

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
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
      val (old, updated) : (Option[FlowTransaction], FlowTransaction) = findTransaction(e.transactionId).get match {
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
          (None, if (e.state == FlowTransactionStateStarted) {
            newT
          } else {
            newT.updateTransaction(e)
          })

        case Some(r) => (Some(r), r.updateTransaction(e))
      }

      store(old, updated).get
    }
  }

  /**
    * @inheritdoc
    */
  override def findTransaction(tid: String): Try[Option[FlowTransaction]] = Try {

    measureDuration(s"Executed find for [$tid] in "){ () =>
      log.trace(s"Trying to find transaction [$tid]")
      val iterator : Iterator[Path] = tidPaths(tid)

      if (iterator.isEmpty) {
        None
      } else {
        val tFile : File = iterator.take(1).toList.head.toFile()
        loadTransaction(tFile).get
      }
    }
  }

  /**
    * @inheritdoc
    */
  override def removeTransaction(tid: String): Unit = Try {

    tidPaths(tid).foreach{ p =>
      val tFile : File = p.toFile()

      if (tFile.delete()) {
        log.trace(s"Deleted transaction file [${tFile.getAbsolutePath()}]")
      } else {
        log.warn(s"Failed to delete transaction file [${tFile.getAbsolutePath()}]")
      }
    }
  }

  /**
    * @inheritdoc
    */
  override def transactions: Iterator[FlowTransaction] = {
    val dirStream : DirectoryStream[Path] = Files.newDirectoryStream(dir.toPath())
    path2transIterator(dirStream.iterator().asScala)
  }


  /**
    * @inheritdoc
    */
  override def completed: Iterator[FlowTransaction] = path2transIterator(stateDirectoryStream(FlowTransactionStateCompleted))

  override def failed: Iterator[FlowTransaction] = path2transIterator(stateDirectoryStream(FlowTransactionStateFailed))

  override def open: Iterator[FlowTransaction] = path2transIterator(
    stateDirectoryStream(FlowTransactionStateStarted, FlowTransactionStateUpdated)
  )


  override def clearTransactions(): Unit = {
    filteredDirectoryStream(_ => true).foreach(_.toFile().delete())
  }

  override def cleanUp(states : FlowTransactionState*): Unit = {
    log.info(s"Performing cleanup for states [${states.mkString(",")}]")
    val start : Long = System.currentTimeMillis()

    val needsCleanUp : Path => Boolean = p => {
      val nameParts : Array[String] = p.toFile().getName().split("\\.")
      if (nameParts.length == 3) {
        try {
          val state : FlowTransactionState = FlowTransactionState.apply(nameParts(2)).get
          val retain : FiniteDuration = state match {
            case FlowTransactionStateFailed => config.retainFailed
            case FlowTransactionStateCompleted => config.retainCompleted
            case _ => config.retainStale
          }
          System.currentTimeMillis() - nameParts(1).toLong >= retain.toMillis
        } catch {
          case NonFatal(_) => false
        }
      } else {
        false
      }
    }

    stateDirectoryStream(states:_*).filter(needsCleanUp).foreach(p => p.toFile().delete())
    log.info(s"Clean up took [${System.currentTimeMillis() - start}]ms")
  }


  private val path2transIterator : Iterator[Path] => Iterator[FlowTransaction] = { pit =>
    pit.map{ p => loadTransaction(p.toFile()) }
      .filter(_.isSuccess)
      .map(_.get)
      .filter(_.isDefined)
      .map(_.get)
  }

  private val tidPaths : String => Iterator[Path] = { tid =>
    val searchTid : Path => Boolean = p => p.toFile().getName().startsWith(tid)
    filteredDirectoryStream(searchTid)
  }

  private def filteredDirectoryStream(f :Path => Boolean) : Iterator[Path] = {
    val tidFilter : DirectoryStream.Filter[Path] = new DirectoryStream.Filter[Path] {
      override def accept(entry: Path): Boolean = f(entry)
    }

    Files.newDirectoryStream(dir.toPath(), tidFilter).iterator().asScala
  }

  private def stateDirectoryStream(states : FlowTransactionState*) : Iterator[Path] = {
    filteredDirectoryStream { p =>
      val s : Array[String] = p.toFile().getName().split("\\.")
      states.map(_.toString).contains(s(s.length -1))
    }
  }

  private val filename : FlowTransaction => String = t =>
    s"${t.tid}.${t.lastUpdate.getTime()}.${t.state}"

  private def store(old: Option[FlowTransaction], changed : FlowTransaction) : Try[FlowTransaction] = Try {

    val json : String = Pickle.intoString(changed)
    val newFile : File = new File(dir, filename(changed))

    old.foreach(t => new File(dir, filename(t)).delete())

    var writer : Option[BufferedWriter] = None

    try {
      writer = Some(Files.newBufferedWriter(newFile.toPath(), charset))
      writer.foreach{ w => w.write(json) }
      changed
    } catch {
      case NonFatal(e) =>
        log.warn(s"Error writing transaction file [${newFile.getAbsolutePath()}][${e.getMessage()}]")
        throw e
    } finally {
      writer.foreach { w =>
        try {
          w.close()
        } catch {
          case NonFatal(e) =>
            log.warn(s"Error closing file [${newFile.getAbsolutePath()}][${e.getMessage()}]")
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


