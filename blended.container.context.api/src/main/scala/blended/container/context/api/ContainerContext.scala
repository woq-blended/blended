package blended.container.context.api

import java.util.concurrent.atomic.AtomicLong

import com.typesafe.config.Config

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
  def getContainerLogDirectory(): String

  def getProfileDirectory(): String
  def getProfileConfigDirectory(): String

  def getContainerHostname(): String

  def getContainerCryptoSupport() : ContainerCryptoSupport

  // application.conf + application_overlay.conf
  def getContainerConfig(): Config

  def getNextTransactionCounter() : Long = ContainerContext.nextTransactionCounter
}
