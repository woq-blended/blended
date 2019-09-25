package blended.streams

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import blended.util.logging.Logger

// A stage that may generate multiple responses from one incoming message
class MultipleResultGraphStage[T, U](f : T => List[U]) extends GraphStage[FlowShape[T,U]] {
  private val in : Inlet[T] = Inlet[T]("MultiResult.in")
  private val out : Outlet[U] = Outlet[U]("MultiResult.out")

  private val log : Logger = Logger(classOf[MultipleResultGraphStage[_,_]].getName())

  override val shape : FlowShape[T, U] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private var pendingValues: List[U] = Nil

    // as long as values are still available, we will push them one by one
    // if no more messages are available to push downstream, we will pull
    // the inlet and eventually a new inbound message will be pulled from
    // upstream, yielding in a new list of values to be pushed.
    private def push(): Unit = {
      pendingValues match {
        case Nil =>
          if (!hasBeenPulled(in)) {
            log.trace("Pulling new input")
            pull(in)
          }
        case h :: t =>
          if (isAvailable(out)) {
            push(out, h)
            log.trace(s"Pushing value [$h], rest is [$t]")
            pendingValues = t
          }
      }
    }

    setHandlers(
      in, out,
      new InHandler with OutHandler {
        override def onPush(): Unit = {
          // We calculate the list of responses
          pendingValues = f(grab(in))
          // and start to push one by one
          push()
        }

        override def onPull(): Unit = push()
      })

    // We start by pulling the inlet
    override def preStart(): Unit = {
      pull(in)
    }
  }
}
