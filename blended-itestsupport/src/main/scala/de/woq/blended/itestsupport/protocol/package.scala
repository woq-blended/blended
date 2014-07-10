package de.woq.blended.itestsupport

package object protocol {
  case object Reset
  case object GetPort
  case class FreePort(p: Int)
}
