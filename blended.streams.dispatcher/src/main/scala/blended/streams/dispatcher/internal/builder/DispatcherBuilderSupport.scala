package blended.streams.dispatcher.internal.builder

import java.util.UUID

import blended.streams.dispatcher.internal.{OutboundRouteConfig, ResourceTypeConfig}
import blended.streams.message.FlowEnvelope
import blended.streams.worklist.{FlowWorklistItem, Worklist, WorklistItem}
import blended.util.logging.Logger
import com.typesafe.config.Config
import blended.util.config.Implicits._

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

trait DispatcherBuilderSupport {

  def containerConfig : Config

  private val headerCfg = containerConfig.getConfig("blended.flow.header")

  def prefix : String = headerCfg.getString("prefix", "Blended")
  def headerTransactionId : String = headerCfg.getString("transactionId", "TransactionId")
  def headerBranchId : String = headerCfg.getString("branchId", prefix + "BranchId")

  val streamLogger : Logger

  val header : String => String = name => prefix + name

  // Keys to stick objects into the FlowEnvelope context
  val appHeaderKey : String = "AppLogHeader"
  val bridgeProviderKey : String = "BridgeProvider"
  val bridgeDestinationKey : String = "BridgeDestination"
  val rtConfigKey : String = classOf[ResourceTypeConfig].getSimpleName()
  val outboundCfgKey : String = classOf[OutboundRouteConfig].getSimpleName()

  val headerResourceType         = "ResourceType"

  def headerBridgeVendor         : String = header("BridgeVendor")
  def headerBridgeProvider       : String = header("BridgeProvider")
  def headerBridgeDest           : String = header("BridgeDestination")

  def headerCbeEnabled           : String = header("CbeEnabled")

  def headerEventVendor          : String = header("EventVendor")
  def headerEventProvider        : String = header("EventProvider")
  def headerEventDest            : String = header("EventDestination")
  def headerAutoComplete         : String = header("AutoCompleteStep")

  def headerBridgeRetry          : String = header("Retry")
  def headerBridgeRetryCount     : String = header("BridgeRetryCount")
  def headerBridgeMaxRetry       : String = header("BridgeMaxRetry")

  def headerTimeToLive           : String = header("TimeToLive")

  /**
  * Access a typed object in the given envelope and the given key. If an object
  * for the key with the propert type is present, the given function will be applied
  * and the result of the function will be returned.
  * If the object is not present in the envelope or has the wrong type, an exception
  * will be returned.
  */
  def withContextObject[T,R](key : String, env: FlowEnvelope)(f : T => Try[R])(implicit classTag: ClassTag[T]) : Either[FlowEnvelope, R] = {

    env.getFromContext[T](key).get match {

      // The object can't be found for the key with the given type
      case None => // Should not be possible
        val e = new MissingContextObject(key, classTag.runtimeClass.getName())
        streamLogger.error(e)(e.getMessage)
        Left(env.withException(e))

      // We have found the object, now we try to apply the function
      case Some(o) =>
        f(o) match {
          case Success(s) => Right(s)
          case Failure(t) => Left(env.withException(t))
        }
    }
  }

  /**
   * Lookup an object from the envelope context and use it within a function to transform
   * the envelope. This is a special case where the result of the given function is also
   * a FlowEnvelope, so that wrapping the result in an Either[...] is not required.
   */
  def withContextObject[T](key : String, env: FlowEnvelope)(f : T => FlowEnvelope)(implicit classTag: ClassTag[T]) : FlowEnvelope = {

    env.getFromContext[T](key).get match {

      case None => // Should not be possible
        val e = new MissingContextObject(key, classTag.runtimeClass.getName())
        streamLogger.error(e)(e.getMessage)
        env.withException(e)

      case Some(o) =>
        f(o)
    }
  }

  def worklistItem(env: FlowEnvelope) : Try[WorklistItem] = Try {
    val id = env.header[String](headerBranchId).get
    FlowWorklistItem(env, id)
  }

  def worklist(envelopes : FlowEnvelope*) : Try[Worklist] = Try {
    envelopes match {
      case Seq() =>
        Worklist(id = UUID.randomUUID().toString(), items = Seq.empty)

      case l =>
        Worklist(l.head.id, l.map(env => worklistItem(env).get))
    }
  }
}
