package blended.prickle.akka.http

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{ContentTypeRange, MediaTypes}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import blended.util.logging.Logger
import microjson.JsValue
import prickle._

trait PrickleSupport {

  private[this] val log = Logger[PrickleSupport]

  val prickleMediaType = MediaTypes.`application/json`

  implicit def toEntityMarshaller[T](implicit p : Pickler[T], config : PConfig[JsValue]) : ToEntityMarshaller[T] = {
    //    Marshaller.stringMarshaller(prickleMediaType) {
    //    Marshaller.charArrayMarshaller(prickleMediaType).wrap(prickleMediaType) {
    Marshaller.StringMarshaller.wrap(prickleMediaType) {
      in : T =>
        log.debug(s"About to pickle: ${in}")
        val pickled = Pickle.intoString(in)
        log.debug(s"pickled: ${pickled}")
        pickled
    }
  }

  implicit def fromEntityUnmarshaller[T](implicit u : Unpickler[T], config : PConfig[JsValue]) : FromEntityUnmarshaller[T] = {
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange(prickleMediaType.toContentType)).map {
      jsonString : String =>
        //    Unmarshaller.charArrayUnmarshaller.forContentTypes(ContentTypeRange(prickleMediaType.toContentType)).map {
        //      in: Array[Char] =>
        //        val jsonString = String.valueOf(in)
        log.debug(s"About to unpickle from json string: ${jsonString}")
        val unpickled = Unpickle[T].fromString(jsonString)
        log.debug(s"unpickled: ${unpickled}")
        unpickled.get
    }
  }

}

object PrickleSupport extends PrickleSupport

