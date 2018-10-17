package blended.streams.dispatcher.internal.builder

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

  def withContextObject[T,R](key : String, env: FlowEnvelope)(f : T => Try[R])(implicit classTag: ClassTag[T]) : Either[FlowEnvelope, R] = {
    env.getFromContext[T](key).get match {

      case None => // Should not be possible
        val e = new MissingContextObject(key, classTag.runtimeClass.getName())
        streamLogger.error(e)(e.getMessage)
        Left(env.withException(e))

      case Some(o) =>
        f(o) match {
          case Success(s) => Right(s)
          case Failure(t) => Left(env.withException(t))
        }
    }
  }

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
}
