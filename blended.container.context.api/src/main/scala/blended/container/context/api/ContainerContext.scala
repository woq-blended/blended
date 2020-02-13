package blended.container.context.api

import java.io.File
import java.util.concurrent.atomic.AtomicLong

import blended.security.crypto.ContainerCryptoSupport
import com.typesafe.config.{Config, ConfigFactory}

object ContainerContext {

  val transactionCounter = new AtomicLong(0)

  def nextTransactionCounter : Long = {
    if (transactionCounter.get() == Long.MaxValue) {
      transactionCounter.set(0L)
    }

    transactionCounter.incrementAndGet()
  }
}

trait ContainerContext {

  def getContainerDirectory() : String
  def getContainerConfigDirectory() : String
  def getContainerLogDirectory() : String

  def getProfileDirectory() : String
  def getProfileConfigDirectory() : String

  def getContainerHostname() : String

  def getContainerCryptoSupport() : ContainerCryptoSupport

  // application.conf + application_overlay.conf
  def getContainerConfig() : Config

  def getConfig(id : String) : Config = {

    ConfigLocator.config(
      new File(getContainerConfigDirectory()), s"$id.conf", getContainerConfig()
    ) match {
      case empty if empty.isEmpty =>
        val cfg = getContainerConfig()
        if (cfg.hasPath(id)) cfg.getConfig(id) else ConfigFactory.empty()

      case cfg => cfg
    }
  }

  def getNextTransactionCounter() : Long = ContainerContext.nextTransactionCounter
}
