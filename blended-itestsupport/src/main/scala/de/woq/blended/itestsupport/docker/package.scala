package de.woq.blended.itestsupport

import scala.language.implicitConversions

package object docker {

  implicit def tuple2NamedPort(p : (String, Int)) = new NamedContainerPort(p._1, p._2)
}
