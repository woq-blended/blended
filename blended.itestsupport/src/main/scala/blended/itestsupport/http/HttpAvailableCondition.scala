package blended.itestsupport.http

import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorSystem
import blended.itestsupport.condition.AsyncCondition

class HttpAvailableCondition(url: String)(implicit val actorSystem: ActorSystem)
  extends AsyncCondition(
    HttpChecker.props(url, 200),
    s"Check of HTTP result code 200 for GET ${url}"
  ) {

}

object HttpAvailableCondition {
  def apply(
    url: String,
    timeout: Option[FiniteDuration] = None
  )(implicit actorSystem: ActorSystem): HttpAvailableCondition = {
    val t = timeout
    new HttpAvailableCondition(url) {
      override def timeout: FiniteDuration = t.getOrElse(super.timeout)
    }
  }
}