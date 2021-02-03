package blended.itestsupport.condition

import akka.actor.Props
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ConditionActor {
  def props(cond: Condition): Props = cond match {
    case pc: ParallelComposedCondition => ParallelConditionActor.props(pc)
    case sc: SequentialComposedCondition => SequentialConditionActor.props(sc)
    case _ => Props(new ConditionActor(cond))
  }

  /**
   * Use this object to query an actor that encapsulates a condition.
   */
  case object CheckCondition

  /**
   * This message collects the results of nested Conditions
   */
  case class ConditionCheckResult(satisfied: List[Condition], timedOut: List[Condition]) {
    def allSatisfied: Boolean = timedOut.isEmpty

    def reportTimeouts: String =
      timedOut.mkString(
        s"\nA total of [${timedOut.size}] conditions have timed out", "\n", ""
      )

    override def toString(): String = s"${getClass().getSimpleName()}(satisfied=${satisfied},timedOut=${timedOut})}"
  }

  object ConditionCheckResult {
    def apply(results: List[ConditionCheckResult]): ConditionCheckResult = {
      new ConditionCheckResult(
        results.flatMap { r => r.satisfied },
        results.flatMap { r => r.timedOut }
      )
    }
  }

}

class ConditionActor(cond: Condition) extends Actor with ActorLogging {
  import ConditionActor._

  case object Tick
  case object Check

  implicit val ctxt : ExecutionContext = context.system.dispatcher
  private var start : Long = 0L

  def receive: Receive = initializing

  def initializing: Receive = {
    case CheckCondition =>
      start = System.currentTimeMillis()
      val requestor = sender()
      System.out.println(s"Starting to check [$cond]")
      log.debug(s"Checking condition [${cond.description}] with timeout [${cond.timeout}] on behalf of [$requestor]")
      val timer = context.system.scheduler.scheduleOnce(cond.timeout, self, Tick)
      context.become(checking(requestor, timer))
      self ! Check
  }

  def checking(checkingFor: ActorRef, timer: Cancellable): Receive = {
    case CheckCondition =>
      log.warning(
        s"""
           |
           |You have sent another CheckCondition message from [${sender()}],
           |but this actor is already checking on behalf of [$checkingFor].
           |
         """.stripMargin
      )
    case Check => cond.satisfied match {
      case true =>
        val duration = (System.currentTimeMillis() - start).millis
        val msg = s"Condition [$cond] is satisfied after [${duration.toMillis}ms]."
        System.out.println(msg)
        log.info(msg)
        timer.cancel()
        val response = ConditionCheckResult(List(cond), List.empty)
        log.debug(s"Answering [$response] to [$checkingFor]")
        checkingFor ! response
        context.stop(self)
      case false =>
        context.system.scheduler.scheduleOnce(cond.interval, self, Check)
    }
    case Tick =>
      val msg = s"Condition [$cond] has timed out."
      log.info(msg)
      System.out.println(msg)
      log.debug(s"Answering to [$checkingFor]")
      checkingFor ! ConditionCheckResult(List.empty, List(cond))
      context.stop(self)
  }
}
