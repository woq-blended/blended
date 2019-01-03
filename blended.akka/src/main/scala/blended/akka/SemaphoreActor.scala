package blended.akka

import java.util.UUID

import akka.actor.{Actor, ActorRef, Terminated}
import blended.util.logging.Logger

object SemaphoreActor {

  case object Acquired
  case object Waiting
  case class Acquire(actor : ActorRef)
  case class Release(actor : ActorRef)
}

class SemaphoreActor extends Actor {
  import SemaphoreActor._

  private val id : String = UUID.randomUUID().toString()
  private val log : Logger = Logger[SemaphoreActor]

  // the pending acquire messages
  private[akka] var pending : List[Acquire] = List.empty

  override def receive: Receive = open

  private[akka] def logPending() : Unit = {
    log.trace(s"Semaphore has [${pending.size}] actors waiting")
  }

  private[akka] def acquire(a : Acquire) : Unit = {
    pending = pending.filterNot(_.actor == a.actor)
    a.actor ! Acquired
    logPending()
    context.become(locked(a))
  }

  private[akka] def release(current : Acquire, a : ActorRef) : Unit = {
    if (current.actor == a) {
      log.trace(s"Releasing actor [$a]")
      context.unwatch(a)
      pending match {
        case Nil => context.become(open)
        case l => acquire(l.last)
      }
    } else {
      pending = pending.filter(_.actor != a)
      logPending()
    }
  }

  private[akka] def open : Receive = {
    case a : Acquire =>
      context.watch(a.actor)
      acquire(a)
    case r : Release => // do nothing

    case m =>
      log.warn(s"Unexpected message of type [${m.getClass().getName()}] in semaphore [$id]")
  }

  private[akka] def locked(lockedBy : Acquire) : Receive = {
    case a : Acquire =>
      context.watch(a.actor)
      if (lockedBy.actor == a.actor) {
        a.actor ! Acquired
      } else {
        pending = (a :: pending).distinct
        logPending()
        a.actor ! Waiting
      }

    case r : Release => release(lockedBy, r.actor)
    case Terminated(a) => release(lockedBy, a)
  }
}
