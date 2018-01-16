package blended.launcher.jvmrunner

import java.io.{IOException, InputStream, OutputStream}

private[jvmrunner]
class RunningProcess(process: Process, errorsIntoOutput: Boolean, interactive: Boolean) {

  private[this] val errThread = asyncCopy(process.getErrorStream, if (errorsIntoOutput) Console.out else Console.err)
  private[this] val inThread = asyncCopy(process.getInputStream, Console.out, interactive)

  private[this] val in = System.in
  private[this] val out = process.getOutputStream

  private[this] val outThread = new Thread("StreamCopyThread") {
    setDaemon(true)

    override def run() {
      try {
        while (true) {
          if (in.available > 0) {
            in.read match {
              case -1 =>
              case read =>
                out.write(read)
                out.flush()
            }
          } else {
            Thread.sleep(50)
          }
        }
      } catch {
        case e: IOException => // ignore
        case e: InterruptedException => // this is ok
      }
    }
  }
  outThread.start()

  def waitFor(): Int = {
    try {
      process.waitFor
    } finally {
      process.getOutputStream().close()
      outThread.interrupt()
      process.getErrorStream().close()
      process.getInputStream().close()
    }
  }

  def stop(): Int = {
    out.close()
    outThread.interrupt()
    waitFor()
  }

  /**
    * Starts a new thread which copies an InputStream into an Output stream. Does not close the streams.
    */

  def asyncCopy(in: InputStream, out: OutputStream, immediately: Boolean = false): Thread =
    new Thread("StreamCopyThread") {
      setDaemon(true)

      override def run() {
        try {
          copy(in, out, immediately)
        } catch {
          case e: IOException => // ignore
          case e: InterruptedException => // ok
        }
        out.flush()
      }

      start()
    }

  /**
    * Copies an InputStream into an OutputStream. Does not close the streams.
    */
  def copy(in: InputStream, out: OutputStream, immediately: Boolean = false): Unit = {
    if (immediately) {
      while (true) {
        if (in.available > 0) {
          in.read match {
            case -1 =>
            case read =>
              out.write(read)
              out.flush()
          }
        } else {
          Thread.sleep(50)
        }
      }
    } else {
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
}