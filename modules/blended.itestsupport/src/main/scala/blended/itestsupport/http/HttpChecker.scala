package blended.itestsupport.http

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success
import akka.actor.Props
import blended.itestsupport.condition.AsyncChecker
import blended.itestsupport.condition.AsyncCondition
import blended.util.logging.Logger
import sttp.client._

/**
 * An actor that checks the response code `responseCode` of an HTTP request to a given URL `url`.
 * @param url The URL to check.
 * @param responseCode The expected response code.
 */
class HttpChecker(url: String, responseCode: Int) extends AsyncChecker {

  private[this] final val log = Logger[HttpChecker]

  override def performCheck(condition: AsyncCondition): Future[Boolean] = {
    val promise = Promise[Boolean]()

    Future {
      val request = basicRequest.get(uri"${url}")
      implicit val backend = HttpURLConnectionBackend()
      val response = request.send()
      log.debug(s"Response: [${response}]")
      response.code
    }.onComplete {
      case Success(code) => promise.success(responseCode == code.code)
      case Failure(e) => promise.success(false)
    }
    promise.future
  }
}

object HttpChecker {
  /**
   * Create the actor props.
   * @param url The URL to check.
   * @param resultCode The expected result code.
   */
  def props(url: String, resultCode: Int = 200): Props = Props(new HttpChecker(url, resultCode))

}
