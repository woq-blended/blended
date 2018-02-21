package blended.security.cert.internal

import blended.security.cert.CommonNameProvider

/**
 * Implementation of a [[CommonNameProvider]] that provides a fixed common name.
 */
class DefaultCommonNameProvider(
  override val commonName: String)
    extends CommonNameProvider {

  override def toString(): String = getClass().getSimpleName + "(commonName=" + commonName + ")"

}