package blended.streams.json

import blended.streams.json.PrickleProtocol._
import blended.streams.message.MsgProperty
import blended.streams.transaction.{FlowTransaction, FlowTransactionGen}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import prickle._

class PrickleProtocolSpec extends LoggingFreeSpec
  with Matchers
  with PropertyChecks {

  "A MsgProperty should" - {

    "be (de)serializable as JSON" in {

      forAll(FlowTransactionGen.propGen){ p =>
        val json : String = Pickle.intoString(p)
        val p2 : MsgProperty = Unpickle[MsgProperty].fromString(json).get

        assert(p === p2)
      }
    }
  }

  "A FlowTransaction should" - {

    "be (de)serializable as JSON" in {

      forAll(FlowTransactionGen.genTrans){ t =>
        val json : String = Pickle.intoString(t)
        val t2 : FlowTransaction = Unpickle[FlowTransaction].fromString(json).get

        assert(t === t2)
      }
    }
  }

}
