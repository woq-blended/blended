package blended.akka

import akka.actor.{Actor, ActorRef}

import scala.collection.mutable.ListBuffer

trait MemoryStash { this : Actor =>

  val requests : ListBuffer[(ActorRef, Any)] = ListBuffer.empty[(ActorRef, Any)]

  def stashing : Receive = {
    case msg =>
      requests.prepend((sender, msg))
  }

  def unstash() : Unit = {
    val r = requests.reverse.toList
    requests.clear()
    r.foreach {
      case (requestor, msg) =>
        self.tell(msg, requestor)
    }
    requests.clear()
  }
}
