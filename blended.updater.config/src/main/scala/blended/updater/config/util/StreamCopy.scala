package blended.updater.config.util

import java.io.OutputStream
import java.io.InputStream

object StreamCopy {

  def copy(in: InputStream, out: OutputStream) {
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