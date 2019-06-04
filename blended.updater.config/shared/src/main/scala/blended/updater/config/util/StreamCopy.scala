package blended.updater.config.util

import java.io.{InputStream, OutputStream}

/**
 * Helper object holding useful methods to copy streams.
 *
 */
object StreamCopy {

  /**
   * Copy a [[InputStream]] `in` to the [[OutputStream]] `out`.
   * This methods blocks as long as the input stream is open.
   * It's the callers responsibility to properly create and close the output stream.
   */
  def copy(in : InputStream, out : OutputStream) : Unit = {
    val buf = new Array[Byte](1024)
    var len = 0
    while ({
      len = in.read(buf)
      len > 0
    }) {
      out.write(buf, 0, len)
    }
  }
}
