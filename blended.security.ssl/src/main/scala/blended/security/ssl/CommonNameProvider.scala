package blended.security.ssl

trait CommonNameProvider {
  
  def commonName(): String

  def alternativeNames() : List[String] = List.empty

}