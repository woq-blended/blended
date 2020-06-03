package blended.streams.multiresult

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.{Done, NotUsed}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.processor.Collector
import blended.util.logging.LogLevel
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

class MultiResultTimeoutException(id : String, timeout : FiniteDuration)
  extends Exception(s"The multiresult sub flow execution for [$id] has timed out after [$timeout]")

object MultiResultController {
  def props(
    replicator : FlowEnvelope => List[FlowEnvelope],
    processSingle : Flow[FlowEnvelope, FlowEnvelope, NotUsed],
    timeout : Option[FiniteDuration],
    log : FlowEnvelopeLogger
  ) : Props = Props(new MultiResultController(replicator, processSingle, timeout, log))
}

class MultiResultController(
  replicator : FlowEnvelope => List[FlowEnvelope],
  processSingle : Flow[FlowEnvelope, FlowEnvelope, NotUsed],
  timeout : Option[FiniteDuration],
  log : FlowEnvelopeLogger
) extends Actor {
  override def receive: Receive = {
    case env : FlowEnvelope =>
      val respondTo : ActorRef = sender()
      log.logEnv(env, LogLevel.Debug, s"Initiating sub flows for envelope [${env.id}]")
      val actor : ActorRef = context.system.actorOf(MultiResultCollector.props(
        replicator, processSingle, respondTo, timeout, log
      ))

      actor ! env
  }
}

object MultiResultCollector {

  def props(
    replicator : FlowEnvelope => List[FlowEnvelope],
    processSingle : Flow[FlowEnvelope, FlowEnvelope, NotUsed],
    respondTo : ActorRef,
    timeout : Option[FiniteDuration],
    log : FlowEnvelopeLogger
  ) : Props = Props(new MultiResultCollector(replicator, processSingle, respondTo, timeout, log))
}

class MultiResultCollector(
  replicator : FlowEnvelope => List[FlowEnvelope],
  processSingle : Flow[FlowEnvelope, FlowEnvelope, NotUsed],
  respondTo : ActorRef,
  timeout : Option[FiniteDuration],
  log : FlowEnvelopeLogger
) extends Actor {

  private val bufferSize : Int = 10
  private case class MultiResultTimeout(timeout : FiniteDuration)

  private implicit val system : ActorSystem = context.system
  private implicit val eCtxt : ExecutionContext = context.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()


  override def receive : Receive = {
    case env : FlowEnvelope =>
      log.logEnv(env, LogLevel.Debug, s"Processing envelope in MultiResultCollector : [${env.id}]")

      val copies : List[FlowEnvelope] = replicator(env)

      copies.map(_.exception).find(_.isDefined) match {
        case Some(Some(t)) =>
          log.logEnv(env, LogLevel.Warn, s"Failed to create copies from envelope [${env.id}] : [${t.getMessage()}]")
          respond(env.withException(t), None)

        case _ =>

          // Here we will collect the individual results
          val collector : Collector[FlowEnvelope] = Collector[FlowEnvelope](
            name = s"MultiResultProcessor-${UUID.randomUUID().toString()}",
            completeOn = Some{ l : Seq[FlowEnvelope] =>
              l.size == copies.size || l.exists(_.exception.isDefined)
            }
          )

          // We need to schedule a Timeout message, which will be triggered if the subflows could not
          // be executed within the given time frame
          val timer : Option[Cancellable] = timeout.map{ t =>
            context.system.scheduler.scheduleOnce(t, self, MultiResultTimeout(t))
          }

          // Once the collector has completed we will pass the result to ourselves
          collector.result.onComplete { r => self ! r }

          // We need to set up the sub flow that accepts messages from an actor and passes
          // each message through the given sub flow while the final result is collected
          // by the collector
          val collActor : ActorRef = Source.actorRef[FlowEnvelope](bufferSize, OverflowStrategy.fail)
            .viaMat(processSingle)(Keep.left)
            .toMat(Sink.actorRef[FlowEnvelope](collector.actor, Done))(Keep.left)
            .run()

          context.become(collecting(env, timer))

          copies.foreach{env => collActor ! env }
      }
  }

  private def collecting(env : FlowEnvelope, timer : Option[Cancellable]) : Receive = {
    case r: Try[_] => r match {
      case Success(Nil) =>
        log.logEnv(env, LogLevel.Info, s"Successfully executed sub flows for [${env.id}]")
        respond(env, timer)
        // We can't match the list element type because of erasure, so we try to match the first element or fail
      case Success(l @ (_: FlowEnvelope) :: _ ) =>
        l.asInstanceOf[List[FlowEnvelope]].map(_.exception).find(_.isDefined).flatten match {
        case None =>
          log.logEnv(env, LogLevel.Info, s"Successfully executed sub flows for [${env.id}]")
          respond(env, timer)
        case Some(t) =>
          log.logEnv(env, LogLevel.Warn, s"Subflow for [${env.id}] threw exception [${t.getMessage()}]")
          respond(env.withException(t), timer)
      }
      case Success(unexpected) =>
        val ex = new RuntimeException(s"Unexpected result type ${unexpected.getClass()}, expected a List[FlowEnvelope]")
        log.logEnv(env, LogLevel.Error, s"Failed to process subflows of [${env.id}] : ${ex.getMessage()}")
        respond(env.withException(ex), timer)
      case Failure(t) =>
        log.logEnv(env, LogLevel.Warn, s"Failed to process subflows of [${env.id}] : ${t.getMessage()}")
        respond(env.withException(t), timer)
    }

    case MultiResultTimeout(t) =>
      val e : Throwable = new MultiResultTimeoutException(env.id, t)
      respond(env.withException(e), None)
  }

  private def respond(env : FlowEnvelope, timer : Option[Cancellable]): Unit = {
    log.logEnv(env, LogLevel.Debug, s"Multiresult processor result is [$env]", false)
    timer.foreach(_.cancel())
    respondTo ! env
    context.stop(self)
  }
}
