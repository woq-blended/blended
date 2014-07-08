package de.woq.blended.itestsupport

import java.io.IOException
import java.net.{DatagramSocket, ServerSocket}

object PortScanner {

  val maxPortNumber = 65535;

  def findFreePort(minPortNumber : Int = 1024) : Int = findFirstFreePort((minPortNumber to maxPortNumber).toList)

  private[PortScanner] def findFirstFreePort(portList: List[Int]): Int = portList match {
    case x :: xs => if (available(x)) x else findFirstFreePort(xs)
    case Nil => throw new IllegalStateException(s"Cannot find a free port ...")
  }

  private[PortScanner] def available(port: Int) = {

    var ss: Option[ServerSocket] = None
    var ds: Option[DatagramSocket] = None

    try {
      ss = Some(new ServerSocket(port))
      ss.get.setReuseAddress(true)
      ds = Some(new DatagramSocket(port))
      ds.get.setReuseAddress(true)
      true
    } catch {
      case ioe: IOException => false
    } finally {
      ds.foreach(_.close)
      ss.foreach(_.close)
    }
  }
}
