package blended.streams.transaction

import java.util.concurrent.atomic.AtomicInteger
import java.util.{Date, UUID}

import blended.streams.message._
import blended.streams.worklist._
import org.scalacheck.Gen

import scala.util.Random

object FlowTransactionGen {

  private val maxKey : Int = 1000
  private val id : AtomicInteger = new AtomicInteger(0)

  private val wlStateGen : Gen[WorklistState] = Gen.oneOf(Seq(
    WorklistStateCompleted,
    WorklistStateStarted,
    WorklistStateFailed,
    WorklistStateTimeout
  ))

  val wlGen : Gen[Map[String, List[WorklistState]]] = Gen.nonEmptyMap(
    for {
      key <- Gen.choose(1, maxKey)
      states <- Gen.nonEmptyListOf(wlStateGen)
    } yield (s"worklist-$key", states.distinct)
  )

  private val intGen : Gen[MsgProperty] = Gen.choose(Int.MinValue, Int.MaxValue)
    .map(i => IntMsgProperty(i))

  private val longGen : Gen[MsgProperty] = Gen.choose(Long.MinValue, Long.MaxValue)
    .map(l => LongMsgProperty(l))

  private val shortGen : Gen[MsgProperty] = Gen.choose(Short.MinValue, Short.MaxValue)
    .map(s => ShortMsgProperty(s))

  private val boolGen : Gen[MsgProperty] = Gen.oneOf(true, false)
    .map(b => BooleanMsgProperty(b))

  private val byteGen : Gen[MsgProperty] = Gen.choose(Byte.MinValue, Byte.MaxValue)
    .map(b => ByteMsgProperty(b))

  private val floatGen : Gen[MsgProperty] = Gen.const(
    FloatMsgProperty(Random.nextFloat())
  )

  private val doubleGen : Gen[MsgProperty] = Gen.const(
    DoubleMsgProperty(Random.nextDouble())
  )

  val propGen : Gen[MsgProperty] = for {
    i <- intGen
    l <- longGen
    s <- shortGen
    b <- boolGen
    by <- byteGen
    f <- floatGen
    d <- doubleGen
    str <- Gen.alphaNumStr.map(s => StringMsgProperty(s))
  } yield Gen.oneOf(i, l, s, b, by, f, d, str).sample.get

  private val creationProps : Gen[Map[String, MsgProperty]] = Gen.nonEmptyMap(
    for {
      key <- Gen.choose(1, maxKey)
      prop <- propGen
    } yield (s"prop-$key", prop)
  )

  def genState : Gen[FlowTransactionState] =
    Gen.oneOf(
      FlowTransactionStateStarted,
      FlowTransactionStateUpdated,
      FlowTransactionStateCompleted,
      FlowTransactionStateFailed
    )

  def genTrans : Gen[FlowTransaction] = for {
    wl <- wlGen
    cp <- creationProps
  } yield FlowTransaction(
      id = s"${id.incrementAndGet()}",
      created = new Date(),
      lastUpdate = new Date(),
      creationProps = cp,
      worklist = wl,
      state = FlowTransaction.transactionState(
        FlowTransactionStateStarted, wl
      )
  )
}
