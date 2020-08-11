package blended.itest.runner

import java.text.SimpleDateFormat
import java.{util => ju}

object TestEvent {

  object State extends Enumeration {
    type State = Value 
    val Started, Failed, Success = Value 
  }
}

case class TestEvent(
  // The name of the factory that has created the test
  factoryName : String,
  // The name of the Test template that has created the test instance
  testName : String,
  // A unique id identifying the test instance 
  id : String,
  // The state of the test 
  state : TestEvent.State.State,
  // The timestamp of the last status change
  timestamp : Long = System.currentTimeMillis(),
  // the exception, if one is encountered in the test run
  cause : Option[Throwable] = None,
) {
  override def toString() : String = {
    val sdf : SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS")
    s"TestEvent($factoryName::$testName($id),$state,${sdf.format(new ju.Date(timestamp))},$cause)"
  }
}