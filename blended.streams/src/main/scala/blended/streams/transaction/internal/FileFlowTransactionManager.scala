package blended.streams.transaction.internal

import java.io._
import java.nio.charset.Charset
import java.nio.file.{DirectoryStream, Files, Path}
import java.util.Date

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import blended.streams.json.PrickleProtocol._
import blended.streams.transaction._
import blended.util.logging.Logger
import prickle._

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.BufferedSource
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

object FileFlowTransactionManager {
  def apply(dir : File)(implicit system : ActorSystem) : FileFlowTransactionManager = new FileFlowTransactionManager(
    FlowTransactionManagerConfig(dir)
  )
}

class FileFlowTransactionManager(
  override val config: FlowTransactionManagerConfig
)(implicit system : ActorSystem) extends FlowTransactionManager {

  private val log : Logger = Logger[FileFlowTransactionManager]
  private val charset : Charset = Charset.forName("UTF-8")

  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()

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
  override def updateTransaction(e: FlowTransactionEvent): Try[FlowTransaction] = {
    val fut : Future[Try[FlowTransaction]] = findTransaction(e.transactionId).map[(Option[FlowTransaction], FlowTransaction)] {
      case None =>
        val now: Date = new Date()
        log.trace(s"Storing new transaction [${e.transactionId}]")

        (None, FlowTransaction(
          id = e.transactionId,
          created = now,
          lastUpdate = now,
          creationProps = e.properties
        ))
      case Some(t) =>
        log.trace(s"Updating transaction [${e.transactionId}]")
        (Some(t), t.updateTransaction(e))
    }.map {
      case (old, updated) => store(old, updated)
    }

    Await.result(fut, 3.seconds)
  }

  /**
    * @inheritdoc
    */
  override def findTransaction(tid: String): Future[Option[FlowTransaction]] = {
    log.trace(s"Trying to find transaction [$tid]")
    mapDirectoryStream(FilteredDirectoryStream.tidStream(tid)).map(_.headOption)
  }

  /**
    * @inheritdoc
    */
  override def removeTransaction(tid: String): Unit =
    withDirectoryStream(FilteredDirectoryStream.tidStream(tid)){ p =>
      log.trace(s"Removing file [${p.toFile().getAbsolutePath()}]")
      p.toFile().delete()
    }

  /**
    * A stream of all known transactions of the container.
    */
  override def withAll(f: FlowTransaction => Boolean): Future[Int] = withDirectoryStream(new FilteredDirectoryStream(_ => true)){ p =>
    loadExistingTransaction(p.toFile()).map(f).getOrElse(false)
  }

  override def cleanUp(states: FlowTransactionState*): Future[Int] = withDirectoryStream(FilteredDirectoryStream.stateDirectoryStream(states:_*)){ p =>
    val n : Array[String] = p.toFile().getName().split("\\.")
    if (n.length == 3) {
      val doDelete : Boolean = Try {
        val state : FlowTransactionState = FlowTransactionState.apply(n(2)).get
        val retain : FiniteDuration = state match {
          case FlowTransactionStateFailed => config.retainFailed
          case FlowTransactionStateCompleted => config.retainCompleted
          case _ => config.retainStale
        }
        System.currentTimeMillis() - n(1).toLong >= retain.toMillis
      }.getOrElse(true)

      if (doDelete) {
        log.trace(s"Cleaning up file [${p.toFile()}]")
        p.toFile().delete()
      } else {
        false
      }

    } else {
      false
    }
  }

  private val filename : FlowTransaction => String = t =>
    s"${t.tid}.${t.lastUpdate.getTime()}.${t.state}"

  private def store(old: Option[FlowTransaction], changed : FlowTransaction) : Try[FlowTransaction] = Try {

    val json : String = Pickle.intoString(changed)
    val newFile : File = new File(dir, filename(changed))

    old.foreach(t => new File(dir, filename(t)).delete())

    var os : Option[OutputStream] = None
    var writer : Option[BufferedWriter] = None

    try {
      os = Some(new FileOutputStream(newFile))
      writer = Some(new BufferedWriter(new PrintWriter(os.get)))
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

      os.foreach { s =>
        try {
          s.close()
        } catch {
          case NonFatal(e) =>
            log.warn(s"Error closing file [${newFile.getAbsolutePath()}][${e.getMessage()}]")
          }
        }
      }
  }

  private def loadExistingTransaction(f : File) : Try[FlowTransaction] = Try {
    loadTransaction(f) match {
      case Failure(t) => throw t
      case Success(None) => throw new Exception(s"FlowTransaction [${f.getName()}] not found")
      case Success(Some(ft)) => ft
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

  private def mapDirectoryStream(dirStream : FilteredDirectoryStream) : Future[Seq[FlowTransaction]] =
    mapDirectoryStreamWithFilter(dirStream)({_ : FlowTransaction => true})

  private def mapDirectoryStreamWithFilter(
    dirStream : FilteredDirectoryStream
  )(
    select : FlowTransaction => Boolean
  ) : Future[Seq[FlowTransaction]] = {

    val transactions : Future[Seq[FlowTransaction]] = dirStream.entries
      .map{ p => loadExistingTransaction(p.toFile()) }
      .filter(_.isSuccess)
      .map(_.get)
      .filter(select)
      .runWith(Sink.seq)

    transactions.onComplete(_ => dirStream.close())

    transactions
  }

  private def withDirectoryStream(
    dirStream : FilteredDirectoryStream
  )(
    f : Path => Boolean
  ) : Future[Int] = {

    val count : Future[Int] = dirStream.entries
      .via(Flow.fromFunction{ p => if (f(p)) 1 else 0 })
      .runFold(0)( (c,v) => c + v )

    count.onComplete(_ => dirStream.close())

    count
  }

  private object FilteredDirectoryStream {

    def tidStream(tid : String) : FilteredDirectoryStream = new FilteredDirectoryStream({ p =>
      p.toFile().getName().startsWith(tid)
    })

    def stateDirectoryStream(states : FlowTransactionState*) : FilteredDirectoryStream = {
      new FilteredDirectoryStream({ p =>
        val s : Array[String] = p.toFile().getName().split("\\.")
        states.map(_.toString).contains(s(s.length -1))
      })
    }
  }

  private class FilteredDirectoryStream(f : Path => Boolean) {

    private val stream : DirectoryStream[Path] = {
      val tidFilter: DirectoryStream.Filter[Path] = new DirectoryStream.Filter[Path] {
        override def accept(entry: Path): Boolean = f(entry)
      }

      Files.newDirectoryStream(dir.toPath(), tidFilter)
    }

    val entries : Source[Path, NotUsed] = Source.fromIterator(() => stream.iterator().asScala)

    def close() : Unit = {
      try {
        stream.close()
      } catch {
        case NonFatal(t) =>
          log.warn(s"Error closing directory stream in transaction cleanup : [${t.getMessage()}]")
      }
    }

  }
}


