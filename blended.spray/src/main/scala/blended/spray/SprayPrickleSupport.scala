package blended.spray

import spray.http._
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller

import prickle._
import org.slf4j.LoggerFactory
import microjson.JsValue

trait SprayPrickleSupport {

  private[this] val log = LoggerFactory.getLogger(classOf[SprayPrickleSupport])

  implicit def prickleMarshaller[T](implicit p: Pickler[T], config: PConfig[JsValue]): Marshaller[T] =
    Marshaller.of[T](ContentTypes.`application/json`) {
      (value, contentType, context) =>
        log.debug("About to pickle: {}", value)
        val pickled = Pickle.intoString(value)
        log.debug("pickled: {}", pickled)
        val entity = HttpEntity(contentType, pickled)
        context.marshalTo(entity)
    }

  implicit def prickleUnmarshaller[T](implicit u: Unpickler[T], config: PConfig[JsValue]): Unmarshaller[T] =
    Unmarshaller[T](MediaTypes.`application/json`) {
      case x: HttpEntity.NonEmpty ⇒
        val jsonString = x.asString(defaultCharset = HttpCharsets.`UTF-8`)
        log.debug("About to unpickle from json: {}", jsonString)
        val unpickled = Unpickle[T].fromString(jsonString)
        log.debug("unpickled: {}", unpickled)
        unpickled.get
    }

}
