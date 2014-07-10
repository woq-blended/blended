package de.woq.blended.itestsupport

import java.io.IOException
import java.net.{DatagramSocket, ServerSocket}

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive

import de.woq.blended.itestsupport.protocol._

trait PortRange {
  val fromPort : Int = 1024
  val toPort   : Int = 65535
}

object PortScanner {
  def apply() = new PortScanner()
}

class PortScanner extends Actor with ActorLogging with PortRange {

  private var minPortNumber = fromPort

  def receive = LoggingReceive {
    case GetPort => {
      val range = minPortNumber to toPort
      range.find {
        available _
      } match {
        case None => self ! Reset
        case Some(port) => {
          log debug s"Found free port [${port}]."
          minPortNumber = port + 1
          sender ! FreePort(port)
        }
      }
    }
    case Reset => {
      minPortNumber = fromPort
    }
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
