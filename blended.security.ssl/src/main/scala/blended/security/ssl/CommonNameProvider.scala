package blended.security.ssl

trait CommonNameProvider {
  
  def commonName(): String

  def alternativeNames() : List[String] = List.empty


  override def toString(): String =
    getClass().getSimpleName + "(commonName=" + commonName + ", altNames = " + alternativeNames() + ")"

}