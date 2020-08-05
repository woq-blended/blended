package blended.itest.runner

import scala.util.Try
import java.{util => ju}

trait TestTemplateFactory {

  /** The name of the template factory */
  def name : String
  /** The templates produced by this factory */
  def templates : List[TestTemplate]

}

/** A simple interface to instantiate a single test. */
trait TestTemplate {
  // The factory that has created this template
  def factory : TestTemplateFactory
  // a unique name for the test template within the Test Template factory 
  def name : String   
  // instantiate a test executable within an Actor
  def test() : Try[Unit]
  // How many instances of the test shall maximal be run 
  def maxExecutions : Int = Int.MaxValue
  // Are multiple parallel instances of the test allowed ?
  def allowParallel : Boolean = true
  // generate an id for the test 
  def generateId : String = ju.UUID.randomUUID().toString()

  override def toString() : String = 
    s"TestTemplate(${factory.name},$name,maxExecutions=$maxExecutions,parallel=$allowParallel)"
}
