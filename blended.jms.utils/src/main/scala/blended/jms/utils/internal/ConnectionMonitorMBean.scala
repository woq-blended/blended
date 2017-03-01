package blended.jms.utils.internal

import java.util.Date

trait ConnectionMonitorMBean {

  def getProvider() : String
  def getStatus() : String
  def getLastConnect() : Option[Date]
  def getLastDisconnect() : Option[Date]
  def getFailedPings() : Int

  def getMaxEvents() : Int
  def setMaxEvents(n : Int) : Unit
  def getEvents() : Array[String]

  def disconnect(reason: String) : Unit
  def connect() : Unit

}
