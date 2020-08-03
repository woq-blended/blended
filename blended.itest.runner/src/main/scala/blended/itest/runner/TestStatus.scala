package blended.itest.runner

import akka.actor.ActorRef

object TestStatus {

  object State extends Enumeration {
    type State = Value 
    val Started, Failed, Success = Value 
  }
}

case class TestStatus(
  // A unique id identifying the test instance 
  id : String,
  // The Actor reference which is responsible for executing the test
  runner : Option[ActorRef],
  // when was the test actually started 
  started : Long, 
  // The state of the test 
  state : TestStatus.State.State,
  // log file entries specific for this test instance 
  testLog : List[String] = List.empty,
)