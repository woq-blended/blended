package blended.itestsupport

import scala.language.implicitConversions

package object docker {

  implicit def tuple3NamedPort(p : (String, Int, Int)) = new NamedContainerPort(p._1, p._2, p._3)
}
