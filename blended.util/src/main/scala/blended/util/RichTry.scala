package blended.util

import scala.util.{Failure, Success, Try}

class RichTry[T](val t : Try[T]) {

  def unwrap : T = t match {
    case Success(v) => v
    case Failure(e) => throw e
  }
}

object RichTry {
  import scala.language.implicitConversions

  implicit def toRichTry[T](t : Try[T]) : RichTry[T] = new RichTry[T](t)
}
