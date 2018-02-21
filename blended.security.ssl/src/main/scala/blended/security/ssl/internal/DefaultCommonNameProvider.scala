package blended.security.ssl.internal

import blended.security.ssl.CommonNameProvider

/**
 * Implementation of a [[CommonNameProvider]] that provides a fixed common name.
 */
class DefaultCommonNameProvider(
  override val commonName: String)
    extends CommonNameProvider {

  override def toString(): String = getClass().getSimpleName + "(commonName=" + commonName + ")"

}