package blended.jms.utils.internal

trait ConnectionMonitorMBean {

  def getProvider() : String
  def getStatus() : String
  def getLastConnect() : String
  def getLastDisconnect() : String
  def getFailedPings() : Int

  def getMaxEvents() : Int
  def setMaxEvents(n : Int) : Unit

  def getEvents() : Array[String]

  def disconnect(reason: String) : Unit
  def connect(now: Boolean) : Unit

}
