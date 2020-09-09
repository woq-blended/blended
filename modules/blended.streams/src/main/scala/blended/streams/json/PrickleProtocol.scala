package blended.streams.json

import blended.streams.message._
import blended.streams.transaction._
import blended.streams.worklist._
import prickle._

object PrickleProtocol {

  implicit val msgPropertyPicklerPair : PicklerPair[MsgProperty] = CompositePickler[MsgProperty]
    .concreteType[IntMsgProperty]
    .concreteType[LongMsgProperty]
    .concreteType[BooleanMsgProperty]
    .concreteType[ByteMsgProperty]
    .concreteType[ShortMsgProperty]
    .concreteType[FloatMsgProperty]
    .concreteType[DoubleMsgProperty]
    .concreteType[StringMsgProperty]
    .concreteType[UnitMsgProperty]

  implicit val wlStatePicklerPair : PicklerPair[WorklistState] = CompositePickler[WorklistState]
    .concreteType[WorklistStateStarted.type]
    .concreteType[WorklistStateCompleted.type]
    .concreteType[WorklistStateTimeout.type]
    .concreteType[WorklistStateFailed.type]

  implicit val tsPicklerPair : PicklerPair[FlowTransactionState] = CompositePickler[FlowTransactionState]
    .concreteType[FlowTransactionStateStarted.type]
    .concreteType[FlowTransactionStateCompleted.type]
    .concreteType[FlowTransactionStateUpdated.type]
    .concreteType[FlowTransactionStateFailed.type]

  implicit val transPickler : Pickler[FlowTransaction] = Pickler.materializePickler[FlowTransaction]
  implicit val transUnpickler : Unpickler[FlowTransaction] = Unpickler.materializeUnpickler[FlowTransaction]
}
