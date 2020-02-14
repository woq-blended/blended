package blended.container.context.impl.internal

import blended.container.context.api.{ContainerContext, ContainerIdentifierService}

import scala.util.Try

abstract class AbstractContainerContextImpl extends ContainerContext {

  private val resolver : ContainerPropertyResolver = new ContainerPropertyResolver(this)

  /**
   * Access to the Container Identifier Service
   */
  override val identifierService: ContainerIdentifierService = new ContainerIdentifierServiceImpl(this)

  /**
   * Access to a blended resolver for config values
   */
  override def resolveString(s: String, additionalProps: Map[String, Any]): Try[AnyRef] = resolver.resolve(s, additionalProps)
}
