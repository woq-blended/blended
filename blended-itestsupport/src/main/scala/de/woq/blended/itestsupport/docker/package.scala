package de.woq.blended.itestsupport

package object docker {

  implicit def tuple2NamedPort(p : (String, Int)) = new NamedContainerPort(p._1, p._2)
}
