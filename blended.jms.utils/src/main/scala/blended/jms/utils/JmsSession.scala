package blended.jms.utils

import akka.actor.ActorSystem
import javax.jms._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

final case class JmsSession(
  session : Session,
  sessionId : String
) {

  def closeSessionAsync()(system : ActorSystem) : Future[Unit] = {

    implicit val eCtxt : ExecutionContext = system.dispatcher

    val p : Promise[Unit] = Promise()

    Future { closeSession() }.onComplete[Unit] {
      case Success(_) => p.success(())
      case Failure(t) => p.failure(t)
    }

    akka.pattern.after(1.seconds, system.scheduler) {
      Future {
        if (!p.isCompleted) {
          p.failure(new Exception(s"Session close for [$sessionId] has timed out"))
        }
      }
    }

    p.future
  }

  def closeSession() : Try[Unit] = Try {
    session.close()
  }

  def abortSessionAsync()(implicit ec : ExecutionContext) : Future[Unit] = Future { abortSession() }

  def abortSession() : Unit = closeSession()

  def createConsumer(
    jmsDestination: JmsDestination,
    selector : Option[String]
  ) : Try[MessageConsumer] = Try {
    (selector, jmsDestination) match {
      case (None, t : JmsDurableTopic) =>
        session.createDurableSubscriber(t.create(session).asInstanceOf[Topic], t.subscriberName)

      case (Some(expr), t : JmsDurableTopic) =>
        session.createDurableSubscriber(t.create(session).asInstanceOf[Topic], t.subscriberName, expr, false)

      case (None, t : JmsTopic) =>
        session.createConsumer(t.create(session))

      case (Some(expr), t : JmsTopic) =>
        session.createConsumer(t.create(session), expr, false)

      case (Some(expr), q) =>
        session.createConsumer(q.create(session).asInstanceOf[Queue], expr)

      case (None, q) =>
        session.createConsumer(q.create(session).asInstanceOf[Queue])
    }
  }
}
