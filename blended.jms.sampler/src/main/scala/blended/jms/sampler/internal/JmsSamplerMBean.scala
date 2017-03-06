package blended.jms.sampler.internal

trait JmsSamplerMBean {

  def getEncoding() : String
  def setEncoding(newEncoding : String) : Unit

  def getDestinationName() : String
  def setDestinationName(newName : String) : Unit

  def isSampling() : Boolean

  def startSampling() : Unit
  def stopSampling() : Unit

}
