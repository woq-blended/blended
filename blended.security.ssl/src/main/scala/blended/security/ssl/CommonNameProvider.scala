package blended.security.ssl

trait CommonNameProvider {
  
  def commonName(): String

}