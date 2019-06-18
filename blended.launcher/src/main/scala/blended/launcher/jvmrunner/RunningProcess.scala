package blended.launcher.jvmrunner

import java.io.{IOException, InputStream, OutputStream}

import blended.util.logging.Logger

import scala.concurrent.duration._

private[jvmrunner] class RunningProcess(
  process : Process,
  errorsIntoOutput : Boolean,
  interactive : Boolean,
  shutdownTimeout : FiniteDuration
) {

  private[this] val in : InputStream = System.in
  private[this] val out : OutputStream = process.getOutputStream

  private[this] val log : Logger = Logger[RunningProcess]
  private[this] val sleepInterval : FiniteDuration = 50.millis

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
            Thread.sleep(sleepInterval.toMillis)
          }
        }
      } catch {
        case e : IOException          => // ignore
        case e : InterruptedException => // this is ok
      }
    }
  }

  if (interactive) {
    log.info("Starting console read thread ...")
    outThread.start()
  } else {
    log.info("Container is started without console read thread ...")
  }

  def waitFor() : Int = {
    try {
      process.waitFor
    } finally {
      process.getOutputStream().close()
      outThread.interrupt()
      process.getErrorStream().close()
      process.getInputStream().close()
    }
  }

  private def waitUntilStopped(t : FiniteDuration) : Boolean = {

    val now : Long = System.currentTimeMillis()
    val end : Long = now + t.toMillis

    while (process.isAlive() && System.currentTimeMillis() <= end) {
      Thread.sleep(sleepInterval.toMillis)
    }

    process.isAlive()
  }

  def stop() : Int = {

    log.info("Stopping container JVM ...")
    if (interactive) {
      outThread.interrupt()
    } else {
      out.write("stop 0\n".getBytes())
    }

    // If the process is still alive after we tried to stop it we will kill it
    if (waitUntilStopped(shutdownTimeout)) {
      log.info("Killing container JVM after maximum shutdown timeout ...")
      process.destroy()
    }

    out.flush()
    out.close()
    waitFor()
  }

  /**
   * Starts a new thread which copies an InputStream into an Output stream. Does not close the streams.
   */

  private def asyncCopy(in : InputStream, out : OutputStream, immediately : Boolean = false) : Thread =
    new Thread("StreamCopyThread") {
      setDaemon(true)

      override def run() {
        try {
          copy(in, out, immediately)
        } catch {
          case e : IOException          => // ignore
          case e : InterruptedException => // ok
        }
        out.flush()
      }

      start()
    }

  /**
   * Copies an InputStream into an OutputStream. Does not close the streams.
   */
  private def copy(in : InputStream, out : OutputStream, immediately : Boolean = false) : Unit = {
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
