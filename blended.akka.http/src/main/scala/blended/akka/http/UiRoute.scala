package blended.akka.http

import akka.http.scaladsl.model
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.StreamConverters

import scala.util.{Failure, Success, Try}

class UiRoute(contentDir : String, cl: ClassLoader) {

  // All resources will be served from the classpath
  val route : Route = extractUnmatchedPath { path =>
    val resourcePath = if (path.toString().endsWith("/"))  {
      s"$contentDir${path.toString()}index.html"
    } else {
      s"$contentDir${path.toString()}"
    }

    val contentType = if (resourcePath.endsWith("html")) ContentTypes.`text/html(UTF-8)` else ContentTypes.`application/octet-stream`

    Try(cl.getResourceAsStream(resourcePath)) match {
      case Success(found) =>
        Option(found) match {
          case Some(s) =>
            complete(model.HttpResponse(
              entity = HttpEntity(
                contentType = contentType,
                data = StreamConverters.fromInputStream( () => s )
              )
            ))
          case None =>
            complete(StatusCodes.NotFound)
        }
      case Failure(e) =>
        complete(StatusCodes.NotFound)
    }
  }
}
