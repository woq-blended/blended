package blended.itestsupport.jolokia

import blended.itestsupport.condition.{AsyncChecker, AsyncCondition}
import blended.jolokia.{JolokiaClient, JolokiaObject}

import scala.concurrent.Future
import scala.util.Try

abstract class JolokiaChecker(client : JolokiaClient) extends AsyncChecker {

  def exec(client : JolokiaClient) : Try[JolokiaObject]
  def assertJolokia(obj : Try[JolokiaObject]) : Boolean

  override def performCheck(condition: AsyncCondition): Future[Boolean] = Future {
    assertJolokia(exec(client))
  }
}
