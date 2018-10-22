package blended.streams.transaction

import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

class FlowTransactionEventSpec extends LoggingFreeSpec
  with Matchers {

  "A FlowTransaction should" - {

    "have an empty worklist after being created" in {

      val t = FlowTransaction.startTransaction()
      t.worklist should be (empty)
      t.state should be (FlowTransactionState.Started)
    }

    "reflect the envelope id as transaction id if created with an envelope" in {

      val env = FlowEnvelope(FlowMessage.noProps)
      val t = FlowTransaction.startTransaction(Some(env))

      t.tid should be (env.id)
    }
  }

}
