package blended.streams.dispatcher.internal.builder

import java.util.UUID

import blended.streams.dispatcher.internal.worklist.{DispatcherWorklistItem, Worklist, WorklistItem}
import blended.streams.dispatcher.internal.{OutboundRouteConfig, ResourceTypeConfig}
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

trait DispatcherBuilderSupport {
  val prefix : String
  val streamLogger : Logger

  // Keys to stick objects into the FlowEnvelope context
  val appHeaderKey : String = "AppLogHeader"
  val rtConfigKey : String = classOf[ResourceTypeConfig].getSimpleName()
  val outboundCfgKey : String = classOf[OutboundRouteConfig].getSimpleName()

  val HEADER_RESOURCETYPE        = "ResourceType"

  val HEADER_BRIDGE_VENDOR       : String = prefix + "BridgeVendor"
  val HEADER_BRIDGE_PROVIDER     : String = prefix + "BridgeProvider"
  val HEADER_BRIDGE_DEST         : String = prefix + "BridgeDestination"

  val HEADER_CBE_ENABLED         : String = prefix + "CbeEnabled"

  val HEADER_EVENT_VENDOR        : String = prefix + "EventVendor"
  val HEADER_EVENT_PROVIDER      : String = prefix + "EventProvider"
  val HEADER_EVENT_DEST          : String = prefix + "EventDestination"
  val HEADER_OUTBOUND_ID         : String = prefix + "OutboundId"

  val HEADER_BRIDGE_RETRY        : String = prefix + "Retry"
  val HEADER_BRIDGE_RETRYCOUNT   : String = prefix + "BridgeRetryCount"
  val HEADER_BRIDGE_MAX_RETRY    : String = prefix + "BridgeMaxRetry"
  val HEADER_BRIDGE_CLOSE        : String = prefix + "BridgeCloseTA"

  val HEADER_TIMETOLIVE          : String = prefix + "TimeToLive"

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
    val id = env.header[String](HEADER_OUTBOUND_ID).get
    DispatcherWorklistItem(env, id)
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
