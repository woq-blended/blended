package blended.updater

import java.io.File

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.event.LoggingReceive
import blended.updater.config.util.Unzipper

import scala.collection.immutable._
import scala.util.Failure
import scala.util.Success

object Unpacker {

  // Messages
  case class Unpack(reqId : String, requestRef : ActorRef, archiveFile : File, targetDir : File)

  // Replies
  sealed trait UnpackReply {
    def reqId : String
  }
  final case class UnpackingFinished(reqId : String) extends UnpackReply
  final case class UnpackingFailed(reqId : String, error : Throwable) extends UnpackReply

  def props() : Props = Props(new Unpacker())

}

/**
 * Blocking unpacker for ZIP files.
 */
class Unpacker() extends Actor with ActorLogging {
  import Unpacker._

  def receive : Actor.Receive = LoggingReceive {
    case Unpack(reqId, requestRef, archiveFile, targetDir) =>
      val blacklist = List("profile.conf", "bundles", "resources")
      Unzipper.unzip(archiveFile, targetDir, Nil,
        fileSelector = Some { fileName : String => !blacklist.exists(fileName == _) },
        placeholderReplacer = None) match {
          case Success(files) => requestRef ! UnpackingFinished(reqId)
          case Failure(e)     => requestRef ! UnpackingFailed(reqId, e)
        }
  }

}
