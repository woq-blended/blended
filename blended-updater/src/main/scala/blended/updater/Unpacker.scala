package blended.updater

import java.io.File
import java.net.URL
import scala.sys.process.urlToProcess
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.actorRef2Scala
import akka.event.LoggingReceive
import akka.actor.ActorRef
import akka.actor.Props
import scala.util.control.NonFatal
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import blended.updater.config.RuntimeConfig
import blended.updater.internal.Unzipper
import scala.util.Failure
import scala.collection.immutable._

object Unpacker {

  // Messages
  case class Unpack(reqId: String, requestRef: ActorRef, archiveFile: File, targetDir: File)

  // Replies
  sealed trait UnpackReply {
    def reqId: String
  }
  final case class UnpackingFinished(reqId: String) extends UnpackReply
  final case class UnpackingFailed(reqId: String, error: Throwable) extends UnpackReply

  def props(): Props = Props(new Unpacker())

}

/**
 * Blocking unpacker for ZIP files.
 */
class Unpacker() extends Actor with ActorLogging {
  import Unpacker._

  def receive: Actor.Receive = LoggingReceive {
    case Unpack(reqId, requestRef, archiveFile, targetDir) =>
      val blacklist = List("profile.conf", "bundles", "resources")
      val placeholderConfig = Unzipper.PlaceholderConfig(
        openSequence = "${",
        closeSequence = "}",
        escapeChar = '\\',
        properties = Map(),
        failOnMissing = true
      )
      Unzipper.unzip(archiveFile, targetDir, Nil,
        fileSelector = Some { fileName: String => !blacklist.exists(fileName == _) },
        placeholderReplacer = None
      ) match {
          case Success(files) => requestRef ! UnpackingFinished(reqId)
          case Failure(e) => requestRef ! UnpackingFailed(reqId, e)
        }
  }

}