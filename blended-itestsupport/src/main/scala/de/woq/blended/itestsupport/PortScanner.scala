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

trait PortChecker {
  def available : (Int => Boolean)
}

object PortScanner {
  def apply(
    minPort: Int = 1024,
    maxPort : Int = 65535,
    portCheck : (Int => Boolean) = { port =>
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
  ) = new PortScanner() with PortRange with PortChecker {
    override val fromPort: Int = minPort
    override val toPort: Int = maxPort
    override def available = portCheck
  }
}

class PortScanner extends Actor with ActorLogging { this : PortRange with PortChecker =>

  var minPortNumber = 0

  def receive = LoggingReceive {
    case GetPort => {
      val range = minPortNumber to toPort
      range.find { port => available(port) } match {
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

  override def preStart() {
    minPortNumber = fromPort
  }
}
