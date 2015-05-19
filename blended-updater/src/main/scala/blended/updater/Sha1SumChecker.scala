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

  private def bytesToString(digest: Array[Byte]): String = {
    import java.lang.StringBuilder
    val result = new StringBuilder(32);
    val f = new Formatter(result)
    digest.foreach(b => f.format("%02x", b.asInstanceOf[Object]))
    result.toString
  }

  def digestFile(file: File): Option[String] = {
    val sha1Stream = new DigestInputStream(new BufferedInputStream(new FileInputStream(file)), MessageDigest.getInstance("SHA"))
    try {
      while (sha1Stream.read != -1) {}
      Some(bytesToString(sha1Stream.getMessageDigest.digest))
    } catch {
      case NonFatal(e) => None
    } finally {
      sha1Stream.close()
    }
  }

  def receive: Actor.Receive = LoggingReceive {
    case msg @ CheckFile(reqId, reqRef, file, sha1Sum) =>
      digestFile(file) match {
        case Some(`sha1Sum`) =>
          reqRef ! ValidChecksum(reqId, file, sha1Sum)
        case other =>
          reqRef ! InvalidChecksum(reqId, file, other)

      }
  }

}