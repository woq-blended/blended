package blended.jmx

/**
  * Use plain case classes as entities to be exposed via JMX.
  */
trait ProductMBeanManager {

  def updateMBean(v : Product) : Unit
  def removeMBean(v : Product) : Unit

  def start() : Unit
  def stop() : Unit
  
}
