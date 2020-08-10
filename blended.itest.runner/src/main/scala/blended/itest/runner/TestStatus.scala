package blended.itest.runner

import akka.actor.ActorRef

object TestStatus {

  object State extends Enumeration {
    type State = Value 
    val Started, Failed, Success = Value 
  }
}

case class TestStatus(
  // The name of the factory that has created the test
  factoryName : String,
  // The name of the Test template that has created the test instance
  testName : String,
  // A unique id identifying the test instance 
  id : String,
  // The Actor reference which is responsible for executing the test
  runner : Option[ActorRef],
  // when was the test actually started 
  started : Long, 
  // The state of the test 
  state : TestStatus.State.State,
  // The timestamp of the event
  timestamp : Long = System.currentTimeMillis(),
  // the exception, if one is encountered in the test run
  cause : Option[Throwable] = None,
  // log file entries specific for this test instance 
  testLog : List[String] = List.empty
)