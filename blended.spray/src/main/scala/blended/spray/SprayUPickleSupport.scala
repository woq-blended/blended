package blended.spray

import spray.http._
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller
import upickle.default.{Reader, Writer, read, writeJs}
import upickle.{Js, json}

trait SprayUPickleSupport {

  implicit def genericMarshaller[T : Writer] : Marshaller[T] =
    Marshaller.delegate(ContentTypes.`application/json`)(writeJs(_))

  implicit def jsValueMarshaller : Marshaller[Js.Value] =
    Marshaller.of[Js.Value](ContentTypes.`application/json`) {
      (value, contentType, context) => {
        val entity = HttpEntity(contentType, json.write(value))
        context.marshalTo(entity)
      }
    }

  implicit def entityUnmarshaller[T : Reader] : Unmarshaller[T] =
    Unmarshaller[T](ContentTypeRange(MediaTypes.`application/json`)) {
      case entity => read[T](entity.asString(HttpCharsets.`UTF-8`))
    }
}
