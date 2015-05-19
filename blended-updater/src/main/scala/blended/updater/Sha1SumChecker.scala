package blended.updater

import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.ActorRef
import java.io.File

object Sha1SumChecker {

  // Messages
  case class CheckFile(requestActor: ActorRef, file: File, sha1Sum: String)

  // Replies
  case class ValidChecksum(file: File, sha1Sum: String)
  case class InvalidChecksum(file: File, actualChecksum: Option[String])

  def props(useParentAsSender: Boolean = false): Props = Props(new Sha1SumChecker(useParentAsSender))

}

class Sha1SumChecker(useParentAsSender: Boolean) extends Actor with ActorLogging {
  import Sha1SumChecker._

  if (useParentAsSender) {
    // replies will go back to parent
    sender().tell("reply", context.parent)
  }

  def receive: Actor.Receive = {
    case CheckFile(reqRef, file, sha1Sum) =>
      // FIXME: implement
      reqRef ! InvalidChecksum(file, None)
  }

}