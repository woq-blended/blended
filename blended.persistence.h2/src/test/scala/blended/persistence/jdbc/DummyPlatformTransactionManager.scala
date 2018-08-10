package blended.persistence.jdbc

import blended.util.logging.Logger
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus

class DummyPlatformTransactionManager extends PlatformTransactionManager {

  private[this] val log = Logger[DummyPlatformTransactionManager]
  private[this] var inTx: TransactionDefinition = _

  def commit(ts: TransactionStatus): Unit = {
    if (inTx == null) {
      throw new IllegalTransactionStateException("No in-flight transaction")
    }
    log.debug("Committing test transaction: " + ts)
    inTx = null
  }

  def rollback(ts: TransactionStatus): Unit = {
    if (inTx == null) {
      throw new IllegalTransactionStateException("No in-flight transaction")
    }
    log.debug("Rollback test transaction: " + ts)
    inTx = null
  }

  def getTransaction(td: TransactionDefinition): TransactionStatus = {
    if (inTx != null) {
      log.warn("There is already a test transaction running: " + inTx)
    }
    log.debug("Creating new test transaction: " + td)
    inTx = td
    new SimpleTransactionStatus()
  }
}