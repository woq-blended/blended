package blended.launcher.jvmrunner

import java.io.{IOException, InputStream, OutputStream}

import blended.util.logging.Logger
import scala.concurrent.duration._

import blended.util.io.StreamCopy
import java.io.BufferedReader
import java.io.InputStreamReader

private[jvmrunner] class RunningProcess(
  process: Process,
  errorsIntoOutput: Boolean,
  interactive: Boolean,
  shutdownTimeout: FiniteDuration
) {

  private[this] val in: InputStream = System.in
  private[this] val out: OutputStream = process.getOutputStream

  private[this] val log: Logger = Logger[RunningProcess]
  private[this] val sleepInterval: FiniteDuration = 50.millis

  private[this] val outThread = asyncCopyThread(in, out, immediately = true, sleepInterval = sleepInterval)

  if (interactive) {
    log.info("Starting console read thread ...")
    outThread.start()
  } else {
    log.info("Container is started without console read thread ...")
  }

  outputThread("stdErr", process.getErrorStream()).start()
  outputThread("stdOut", process.getInputStream()).start()

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

  private def waitUntilStopped(t: FiniteDuration): Boolean = {

    val now: Long = System.currentTimeMillis()
    val end: Long = now + t.toMillis

    while (process.isAlive() && System.currentTimeMillis() <= end) {
      Thread.sleep(sleepInterval.toMillis)
    }

    process.isAlive()
  }

  def stop(): Int = {

    log.info("Stopping container JVM ...")
    if (interactive) {
      outThread.interrupt()
    } else {
      out.write("stop 0\n".getBytes())
    }

    // If the process is still alive after we tried to stop it we will kill it
    if (waitUntilStopped(shutdownTimeout)) {
      log.info(s"Killing container JVM after maximum shutdown timeout of [$shutdownTimeout]")
      process.destroy()
    }

    out.flush()
    out.close()
    waitFor()
  }

  private def outputThread(name: String, is: InputStream): Thread =
    new Thread(name) {
      setDaemon(true)

      override def run(): Unit = {
        val rd = new BufferedReader(new InputStreamReader(is, "UTF-8"))
        try {
          var line = rd.readLine
          while (line != null) {
            Logger(s"blended.launcher.jvmrunner.$name").debug(line)
            line = rd.readLine
          }
        } finally {
          rd.close
        }
      }
    }

  /**
   * Starts a new thread which copies an InputStream into an Output stream. Does not close the streams.
   */
  private def asyncCopyThread(
    in: InputStream,
    out: OutputStream,
    immediately: Boolean,
    sleepInterval: FiniteDuration
  ): Thread =
    new Thread("StreamCopyThread") {
      setDaemon(true)

      override def run(): Unit = {
        try {
          copy(in, out, immediately, sleepInterval)
        } catch {
          case e: IOException          => // ignore
          case e: InterruptedException => // ok
        }
        out.flush()
      }
    }

  /**
   * Copies an InputStream into an OutputStream. Does not close the streams.
   */
  private def copy(in: InputStream, out: OutputStream, immediately: Boolean, sleepInterval: FiniteDuration): Unit = {
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
          Thread.sleep(sleepInterval.toMillis)
        }
      }
    } else {
      StreamCopy.copy(in, out)
    }
  }
}
