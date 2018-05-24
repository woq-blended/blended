package blended.security.ssl

import scala.util.{Success, Try}

trait CommonNameProvider {
  
  def commonName(): Try[String]

  def alternativeNames() : Try[List[String]] = Success(List.empty)

  override def toString(): String =
    getClass().getSimpleName + "(commonName=" + commonName + ", altNames = " + alternativeNames() + ")"

}