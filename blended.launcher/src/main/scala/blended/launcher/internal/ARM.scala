package blended.launcher.internal

import scala.util.control.NonFatal

/**
 * Created by lefou on 11.04.16.
 */
object ARM {

  def using[C <: AutoCloseable, R](c : C)(f : C => R) : R = {
    try {
      f(c)
    } finally {
      try {
        c.close()
      } catch {
        case NonFatal(t) => t.addSuppressed(t)
      }
    }
  }
}
