/*
  The AckSource logic encapsulates the underlying logic for an arbitrary Source 
  of FlowEnvelopes. The enevelopes will be passed down stream and must be 
  acknowledged or denied eventually. An acknowledgement that takes too long, 
  will be treated as a denial as well. 
  
  A concrete implementation must implement the actions to be executed upon acknowledgement 
  and denial. For example, a JMS source would use a JMS acknowledge on the underlying 
  JMS message and a session.recover() upon a denial. A file system based source may move or 
  delete the original file upon acknowledge and restore the original file upon denial.
  
  After picking up an envelope from an external system, the envelope will be considered to 
  be infligth until either an acknowledge or deny has been called. The AckSource logic defines 
  the maximum number of messages that can be inflight at any moment in time. No further messages 
  will be pulled from the external system until a free inflight slot will become available.
  
  Concrete implementations must:
  
  - create and maintain any technical connections required to poll the external system
  - map the inbound data to a FlowEnvelope 
  - implement the concrete actions for acknowledgement and denial
*/

/* 
  We will use an AcknowledgeContext to hold an inflight envelope and the id of the infliht slot
  it is using along with any additional that might be required to perform an acknowledge or denial. 
  
  As a result, each poll of the external system will produce an Option[AcknowledgeContext] which 
  will then be insrted into a free inflight slot. As a consequence, polling of the external system 
  will only be performed if and only if a free inflight slot is available.
  
  As long as free inflight slots are available and no external messages are available, the 
  poll will be executed \in regular intervals. Concrete implementations may overwrite the 
  the nextPoll() method to modify the calculation of the next polling occurrence.
*/

class AcknowldgeContext(
  // The associated inflight id 
  inflightId : String,
  // the underlying FlowEnvelope
  envelope : FlowEnvelope
  // the acknowldege state (pending, acknowledged or denied)
  state : AckState
)

trait AckSourceLogic[T <: AcknowledgeContext] { this : TimerGraphStageLogic =>

  // start the Timer to regularly process acknowldgements 
  
  /* The id to identify the instance in the log files */   
  def id : String

  private val out : Outlet[FlowEnvelope] = Outlet[FlowEnvelope](s"$id-out")
  
  protected def nextPoll() : FiniteDuration = ???
  
  // A callback to fail the stage 
  protected val fail = getAsyncCallback[Throwable] = { t =>
    log.error(t)(s"Failing stage [$id]")
    failStage(t)
  }
  
  // A callback to immediately schedule the next poll
  protected val pollImmediately = getAsyncCallback[Unit] = poll()
  
  /** The id's of the available inflight slots */ 
  protected def inflightSlots() : List[String]
  
  protected def beforeAcknowledge(ackCtxt : T) : Unit = {}
  // this will be called whenever an inflight message has been acknowledged 
  private def acknowledged(ackCtxt : T) : Unit = {
    // First, we need to call beforeAcknowldege(), so that concrete implementations 
    // can perform the technical acknowledgement
    log.debug(s"Flow envelope [${ackCtxt.envelope.id}] has been acknowledged")
    beforeAcknowledge()
    // Then we clear the message from the inflight map 
    removeInflight(ackCtxt.inflightId)
    // finally we can immediately schedule another poll
    pollImmediately.invoke()
  }
  
  
  protected def beforeDenied(ackCtxt : T) : Unit
  // this will be called whenever an inflight message has been denied 
  private def denied(ackCtxt: T) : Unit = {
    log.debug(s"Flow Envelope [${ackCtxt.envelope.id}] has been denied")
    beforeDenied(ackCtxt)
    // we need to clean up the inflight map 
    removeInflight(ackCtxt.inflightId)
    //Then we can immediately poll for a new message 
    pollImmediately.invoke()
  }
  
  // this will be called whenever the acknowledgement for an inflight 
  // message has timed out. Per default this will be delegated to the 
  // denied() handler
  protected def ackTimeout(ackCtxt : T) = denied(ackCtxt)
  
  // The map of current inflight AcknowledgeContexts. An inflight slot is considered 
  // to be available if it's id does not occurr in the keys of the inflight map. 
  private var inflightMap : mautable.Map[String, T] = mutable.Map.empty
  
  private def freeInflightSlot() : Option[String] =
    inflightSlots().find { id => 
      !inflightMap.keys().contains(id)
    }
      
  /* Concrete implementations must implement this method to realize the technical 
     poll from the external system */    
  protected def doPerformPoll(id : String) : Try[Option[T]]
   
  /* Perform a poll of the external system within the context of a free inflight slot */
  private def performPoll(id : String) : Unit = Try {
    // make sure, the outlet has been pulled. If that is not the case, 
    // the outhandler will be called eventually, triggering another 
    // poll()
    if (out.isAvailable) {
      doPerformPoll(id).get match {
        case None => 
          // No message available, schedule next poll
          scheduleOnce(Poll, nextPoll())
        case Some(ackCtxt) =>
          // add the context to the inflight messages
          addInflight(id, ackCtxt)
          // push the envelope to the outlet
          put.push(ackCtxt.envelope)
      }
    }
  }

  /* A logger that must be defined by concrete implementations */
  
  protected def log : Logger
  
  /** 
    Poll the external system whether a new message is available. 
    If a message can be polled without any exceptions, the resulting envelope 
    will be wrapped in an AcknowledgeContext and returned. 
    If no message is available, the result will be Success(None)
    
    Any exception while polling for a message will deny all remaining inflight 
    messages and then fail the stage. 
  */
  
  protected def poll() : Unit = {
    // select a free inflight slot and trigger to poll and process the next inbound 
    // message    
    freeInflightSlot().foreach(performPoll)
  }
  
  /*
     Finally set the handler for the outlet. 
  */
  
  setHandler(out, new OutHandler() {
    override def onPull() : Unit = poll()
  })
}