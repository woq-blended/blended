package blended.launcher.internal

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
        case t : Throwable => t.addSuppressed(t)
      }
    }
  }
}
