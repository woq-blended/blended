package blended.updater

import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.ActorRef
import java.io.File
import akka.event.LoggingReceive
import java.security.MessageDigest
import java.security.DigestInputStream
import java.io.BufferedInputStream
import java.util.Formatter
import java.io.FileInputStream
import scala.util.control.NonFatal
import blended.updater.config.RuntimeConfig

object Sha1SumChecker {

  // Messages
  case class CheckFile(reqId: String, requestActor: ActorRef, file: File, sha1Sum: String)

  // Replies
  case class ValidChecksum(reqId: String, file: File, sha1Sum: String)
  case class InvalidChecksum(reqId: String, file: File, actualChecksum: Option[String])

  def props(): Props = Props(new Sha1SumChecker())

}

class Sha1SumChecker() extends Actor with ActorLogging {
  import Sha1SumChecker._

  def receive: Actor.Receive = LoggingReceive {
    case msg @ CheckFile(reqId, reqRef, file, sha1Sum) =>
      RuntimeConfig.digestFile(file) match {
        case Some(`sha1Sum`) =>
          reqRef ! ValidChecksum(reqId, file, sha1Sum)
        case other =>
          reqRef ! InvalidChecksum(reqId, file, other)

      }
  }

}