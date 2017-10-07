package blended.mgmt.ui.backend

import blended.mgmt.ui.util.Logger
import microjson.JsValue
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.XMLHttpRequest
import prickle.PConfig
import prickle.Pickle
import prickle.Pickler
import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

object AjaxHelper {

  private[this] val log = Logger[AjaxHelper.type]

  def post(url: String, data: String): Future[XMLHttpRequest] = {

    import scala.concurrent.ExecutionContext.Implicits.global

    log.trace(s"About so send POST request to url: ${url} with data: ${data}")
    val f = Ajax.post(url, data)
    f.onComplete {
      case Success(baseUrl) =>
        log.trace(s"Successfully send post request to ${url}")
      case Failure(e) =>
        log.error(s"Could not send post request to ${url}", e)

    }
    f
  }

}