package blended.security.ssl.internal

import blended.security.ssl.CommonNameProvider

/**
 * Implementation of a [[CommonNameProvider]] that provides a fixed common name.
 */
class DefaultCommonNameProvider(
  override val commonName: String,
  logicalHostnames: List[String]
) extends CommonNameProvider {

  override def alternativeNames(): List[String] = {
    logicalHostnames
  }
}