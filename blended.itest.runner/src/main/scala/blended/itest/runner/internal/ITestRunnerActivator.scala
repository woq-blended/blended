package blended.itest.runner.internal

import domino.DominoActivator
import blended.util.logging.Logger

class ITestRunnerActivator extends DominoActivator {

  private val log : Logger = Logger[ITestRunnerActivator]

  whenBundleActive {
    log.info(s"Starting integration test runner")
  }
  
}
