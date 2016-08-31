package blended.akka

import akka.actor.{Actor, ActorLogging, ActorRef}

import scala.collection.mutable.ListBuffer

trait MemoryStash { this : Actor with ActorLogging =>

  val requests = ListBuffer.empty[(ActorRef, Any)]

  def stashing : Receive = {
    case msg =>
      log.debug(s"Stashing [${msg}]")
      requests.prepend((sender, msg))
  }

  def unstash() : Unit = {
    log.debug(s"Unstashing [${requests.size}] messages.")
    val r = requests.reverse.toList
    requests.clear()
    r.foreach { case (requestor, msg) =>
      self.tell(msg, requestor)
    }
    requests.clear()
  }
}
